package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Rules for creating and managing rides (UR-2). Joining and leaving belong to MembershipService. */
public class RideService {

    private static final long CANCEL_CUTOFF_MILLIS =
            TimeUnit.HOURS.toMillis(Constants.CANCEL_LEAVE_CUTOFF_HOURS);

    private final RideRepository repository;
    private final Clock clock;

    public RideService(RideRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void createRide(String title, Location origin, Location destination,
                           Timestamp departureTime, int maxRiders, long expectedCostCents,
                           String note, String organizerId, String organizerName,
                           Callback<Ride> callback) {
        long now = clock.nowMillis();
        try {
            if (organizerId == null || organizerId.isEmpty()) {
                throw new RideRuleException("You must be logged in to create a ride.");
            }
            if (title == null || title.trim().isEmpty()) {
                throw new RideRuleException("Enter a title.");
            }
            if (origin == null || !origin.isValid()) {
                throw new RideRuleException("Choose a valid origin.");
            }
            if (destination == null || !destination.isValid()) {
                throw new RideRuleException("Choose a valid destination.");
            }
            if (departureTime == null || millis(departureTime) <= now) {
                throw new RideRuleException("Departure time must be in the future.");
            }
            if (maxRiders < Constants.MIN_RIDERS || maxRiders > Constants.MAX_RIDERS) {
                throw new RideRuleException("Maximum riders must be between "
                        + Constants.MIN_RIDERS + " and " + Constants.MAX_RIDERS + ".");
            }
            if (expectedCostCents <= 0) {
                throw new RideRuleException("Expected cost must be more than $0.");
            }
        } catch (RideRuleException e) {
            callback.onError(e);
            return;
        }
        repository.create(Ride.createNew(title.trim(), origin, destination, departureTime, maxRiders,
                expectedCostCents, note, organizerId, organizerName, new Timestamp(new Date(now))), callback);
    }

    public void getRide(String rideId, Callback<Ride> callback) {
        repository.getById(rideId, callback);
    }

    /** Marks the ride Delayed with a new time. The original departure time is kept (UR-2.5, UR-2.6). */
    public void markDelayed(String rideId, String userId, Timestamp newDepartureTime, Callback<Ride> callback) {
        repository.update(rideId, ride -> {
            requireOrganizer(ride, userId);
            requireNotFinal(ride);
            if (newDepartureTime == null
                    || millis(newDepartureTime) <= clock.nowMillis()
                    || millis(newDepartureTime) <= millis(ride.getDepartureTime())) {
                throw new RideRuleException("New departure time must be after the original time and in the future.");
            }
            ride.setActivityStatus(ActivityStatus.DELAYED);
            ride.setDelayedDepartureTime(newDepartureTime);
        }, callback);
    }

    /** Puts a Delayed ride back on its original time, unless that time has already passed. */
    public void markOnTime(String rideId, String userId, Callback<Ride> callback) {
        repository.update(rideId, ride -> {
            requireOrganizer(ride, userId);
            requireNotFinal(ride);
            if (clock.nowMillis() >= millis(ride.getDepartureTime())) {
                throw new RideRuleException("The original departure time has passed.");
            }
            ride.setActivityStatus(ActivityStatus.ON_TIME);
            ride.setDelayedDepartureTime(null);
        }, callback);
    }

    /** Cancels the ride if at least 3 hours remain before the original departure time (UR-2.7). */
    public void cancelRide(String rideId, String userId, Callback<Ride> callback) {
        repository.update(rideId, ride -> {
            requireOrganizer(ride, userId);
            requireNotFinal(ride);
            if (millis(ride.getDepartureTime()) - clock.nowMillis() < CANCEL_CUTOFF_MILLIS) {
                throw new RideRuleException("A ride can't be cancelled within "
                        + Constants.CANCEL_LEAVE_CUTOFF_HOURS + " hours of departure.");
            }
            ride.setActivityStatus(ActivityStatus.CANCELLED);
            ride.syncCapacityStatus();
        }, callback);
    }

    /**
     * Throws RideRuleException unless this user can mark the ride Completed now (UR-2.9, UR-5.1).
     * CostService calls this inside the atomic update that saves the final cost.
     */
    public void checkCanComplete(Ride ride, String userId) {
        requireOrganizer(ride, userId);
        requireNotFinal(ride);
        if (clock.nowMillis() < millis(ride.getCurrentDepartureTime())) {
            throw new RideRuleException("A ride can't be completed before its departure time.");
        }
    }

    private static void requireOrganizer(Ride ride, String userId) {
        if (!ride.isOrganizer(userId)) {
            throw new RideRuleException("Only the organizer can do this.");
        }
    }

    private static void requireNotFinal(Ride ride) {
        if (ride.isFinished()) {
            throw new RideRuleException("This ride is already "
                    + (ride.getActivityStatus() == ActivityStatus.CANCELLED ? "cancelled." : "completed."));
        }
    }

    private static long millis(Timestamp timestamp) {
        return timestamp.toDate().getTime();
    }
}
