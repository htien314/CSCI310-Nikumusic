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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CostService {

    private final RideRepository repository;
    private final RideService rideService;
    private final Clock clock;

    // Keep the original constructor for existing tests.
    public CostService() {
        repository = null;
        rideService = null;
        clock = null;
    }

    // Use this constructor for real ride completion.
    public CostService(RideRepository repository,
                       RideService rideService,
                       Clock clock) {
        this.repository = repository;
        this.rideService = rideService;
        this.clock = clock;
    }

    // Calculate shares before saving a completed ride.
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
            result.put(entry.getKey(), new CostShare(
                    entry.getKey(), entry.getValue()));
        }

        return result;
    }

    // Complete a ride using one atomic Firestore transaction.
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

        if (rideId == null || rideId.trim().isEmpty()
                || currentUserId == null || currentUserId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException(
                    "A ride and signed-in user are required."));
            return;
        }

        // Capture defensive input copies before the transaction.
        Map<String, Long> requestedShares = customShares == null
                ? null : new LinkedHashMap<>(customShares);

        repository.update(rideId, ride -> {

            // Check the latest ride state inside the transaction.
            rideService.checkCanComplete(ride, currentUserId);

            List<String> participantIds = new ArrayList<>();
            if (ride.getParticipants() == null) {
                throw new IllegalArgumentException(
                        "Ride participants are missing.");
            }

            for (Participant participant : ride.getParticipants()) {
                if (participant == null
                        || participant.getUserId() == null
                        || participant.getUserId().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "A ride participant has an invalid ID.");
                }
                participantIds.add(participant.getUserId());
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

    // Record an individual rider's payment after ride completion.
    public void markRiderPaid(
            String rideId,
            String organizerId,
            String riderId,
            Callback<Ride> callback) {

        if (repository == null) {
            callback.onError(new IllegalStateException(
                    "Payment tracking is not configured."));
            return;
        }

        if (rideId == null || rideId.trim().isEmpty()
                || organizerId == null || organizerId.trim().isEmpty()
                || riderId == null || riderId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException(
                    "Ride, organizer, and rider IDs are required."));
            return;
        }

        repository.update(rideId, ride -> {

            if (!ride.isOrganizer(organizerId)) {
                throw new IllegalStateException(
                        "Only the organizer can record payments.");
            }

            if (ride.getActivityStatus() != ActivityStatus.COMPLETED) {
                throw new IllegalStateException(
                        "The ride must be completed first.");
            }

            Map<String, CostShare> shares = ride.getCostShares();

            if (shares == null || !shares.containsKey(riderId)) {
                throw new IllegalArgumentException(
                        "This rider has no cost share.");
            }

            CostShare share = shares.get(riderId);

            if (share == null) {
                throw new IllegalArgumentException(
                        "This rider has no valid cost share.");
            }

            if (share.isPaid()) {
                throw new IllegalStateException(
                        "This rider has already paid.");
            }

            share.markPaid();
        }, callback);
    }

}
