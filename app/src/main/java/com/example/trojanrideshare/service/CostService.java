package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class CostService {

    private static final long OVERDUE_MILLIS =
            TimeUnit.DAYS.toMillis(7);

    private final RideRepository repository;
    private final RideService rideService;
    private final Clock clock;

    // Keep the original constructor for calculation tests.
    public CostService() {
        repository = null;
        rideService = null;
        clock = null;
    }

    // Use this constructor for Firestore operations.
    public CostService(RideRepository repository,
                       RideService rideService,
                       Clock clock) {
        this.repository = repository;
        this.rideService = rideService;
        this.clock = clock;
    }

    // Calculate the final shares for an organizer-led completion.
    public Map<String, CostShare> prepareCompletion(
            String organizerId,
            String currentUserId,
            boolean completed,
            long totalCents,
            List<String> participantIds,
            Map<String, Long> customShares) {

        if (organizerId == null
                || !organizerId.equals(currentUserId)) {
            throw new IllegalStateException(
                    "Only the organizer can complete this ride.");
        }

        if (completed) {
            throw new IllegalStateException(
                    "This ride is already completed.");
        }

        Map<String, Long> amounts;

        if (customShares == null) {
            amounts = CostCalculator.splitEqual(
                    totalCents, participantIds);
        } else {
            CostCalculator.validateCustom(
                    totalCents, participantIds, customShares);
            amounts = customShares;
        }

        Map<String, CostShare> result = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            result.put(entry.getKey(),
                    new CostShare(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    // Save ride completion and all final shares atomically.
    public void completeRide(
            String rideId,
            String currentUserId,
            long totalCents,
            Map<String, Long> customShares,
            Callback<Ride> callback) {

        if (repository == null || rideService == null || clock == null) {
            callback.onError(new IllegalStateException(
                    "Ride completion is not configured."));
            return;
        }

        if (isBlank(rideId) || isBlank(currentUserId)) {
            callback.onError(new IllegalArgumentException(
                    "A ride and signed-in user are required."));
            return;
        }

        Map<String, Long> requestedShares = customShares == null
                ? null : new LinkedHashMap<>(customShares);

        repository.update(rideId, ride -> {

            // Validate against the latest Firestore ride state.
            rideService.checkCanComplete(ride, currentUserId);

            if (ride.getParticipants() == null
                    || ride.getParticipants().isEmpty()) {
                throw new IllegalArgumentException(
                        "Ride participants are missing.");
            }

            List<String> participantIds = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            for (Participant participant : ride.getParticipants()) {
                if (participant == null
                        || isBlank(participant.getUserId())) {
                    throw new IllegalArgumentException(
                            "A ride participant has an invalid ID.");
                }

                String id = participant.getUserId();

                if (!seenIds.add(id)) {
                    throw new IllegalArgumentException(
                            "Duplicate ride participant.");
                }

                participantIds.add(id);
            }

            Map<String, CostShare> shares = prepareCompletion(
                    ride.getOrganizerId(),
                    currentUserId,
                    ride.isFinished(),
                    totalCents,
                    participantIds,
                    requestedShares);

            ride.setActualCostCents(totalCents);
            ride.setCostShares(shares);
            ride.setCompletedAt(
                    new Timestamp(new Date(clock.nowMillis())));
            ride.setActivityStatus(ActivityStatus.COMPLETED);
            ride.syncCapacityStatus();

        }, callback);
    }

    // A rider can mark only their own share as Paid.
    public void markRiderPaid(
            String rideId,
            String currentUserId,
            String riderId,
            Callback<Ride> callback) {

        if (repository == null) {
            callback.onError(new IllegalStateException(
                    "Payment tracking is not configured."));
            return;
        }

        if (isBlank(rideId)
                || isBlank(currentUserId)
                || isBlank(riderId)) {
            callback.onError(new IllegalArgumentException(
                    "Ride and user IDs are required."));
            return;
        }

        if (!currentUserId.equals(riderId)) {
            callback.onError(new IllegalStateException(
                    "You can only mark your own share as paid."));
            return;
        }

        repository.update(rideId, ride -> {

            requireCompletedRide(ride);

            CostShare share = requireShare(ride, riderId);

            if (share.isPaid()) {
                throw new IllegalStateException(
                        "This rider has already paid.");
            }

            // Paid is permanent and also clears an overdue flag.
            share.markPaid();

        }, callback);
    }

    // Detect whether an unpaid share is at least 7 days old.
    public boolean isPaymentOverdue(Ride ride, String riderId) {
        if (clock == null || ride == null
                || ride.getActivityStatus() != ActivityStatus.COMPLETED
                || ride.getCompletedAt() == null
                || ride.getCostShares() == null
                || isBlank(riderId)) {
            return false;
        }

        CostShare share = ride.getCostShares().get(riderId);

        if (share == null || share.isPaid()) {
            return false;
        }

        long completedAt =
                ride.getCompletedAt().toDate().getTime();
        long now = clock.nowMillis();

        return now >= completedAt
                && now - completedAt >= OVERDUE_MILLIS;
    }

    // Only the organizer can flag a rider after 7 days.
    public void flagRider(
            String rideId,
            String currentUserId,
            String riderId,
            Callback<Ride> callback) {

        if (repository == null || clock == null) {
            callback.onError(new IllegalStateException(
                    "Overdue tracking is not configured."));
            return;
        }

        if (isBlank(rideId)
                || isBlank(currentUserId)
                || isBlank(riderId)) {
            callback.onError(new IllegalArgumentException(
                    "Ride and user IDs are required."));
            return;
        }

        repository.update(rideId, ride -> {

            if (!ride.isOrganizer(currentUserId)) {
                throw new IllegalStateException(
                        "Only the organizer can flag riders.");
            }

            requireCompletedRide(ride);

            CostShare share = requireShare(ride, riderId);

            if (share.isPaid()) {
                throw new IllegalStateException(
                        "Paid riders cannot be flagged.");
            }

            if (!isPaymentOverdue(ride, riderId)) {
                throw new IllegalStateException(
                        "A rider can only be flagged after 7 days.");
            }

            if (share.isFlagged()) {
                throw new IllegalStateException(
                        "This rider is already flagged.");
            }

            share.setFlagged(true);

        }, callback);
    }

    // Kept for compatibility, but automatic flag writes are disabled.
    @Deprecated
    public void refreshOverdueFlags(
            String rideId,
            Callback<Ride> callback) {

        callback.onError(new UnsupportedOperationException(
                "Use organizer-controlled flagRider instead."));
    }

    private static void requireCompletedRide(Ride ride) {
        if (ride == null
                || ride.getActivityStatus() != ActivityStatus.COMPLETED
                || ride.getCostShares() == null
                || ride.getCompletedAt() == null) {
            throw new IllegalStateException(
                    "The ride must be completed first.");
        }
    }

    private static CostShare requireShare(
            Ride ride, String riderId) {

        CostShare share = ride.getCostShares().get(riderId);

        if (share == null) {
            throw new IllegalArgumentException(
                    "This rider has no valid cost share.");
        }

        return share;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
