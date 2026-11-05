package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Membership rules for one action, applied inside the app's Firestore transaction (Java port of
 * the team's functions/membership.js, so the free Spark plan needs no Cloud Functions).
 * Firestore rules re-check who may change what, and the time cutoffs, against server time.
 */
public final class MembershipTransition {

    public static final String REQUEST = "request";
    public static final String CANCEL = "cancel";
    public static final String ACCEPT = "accept";
    public static final String REJECT = "reject";
    public static final String LEAVE = "leave";
    public static final String CANCEL_RIDE = "cancelRide";

    static final long THREE_HOURS_MILLIS = 3L * 60 * 60 * 1000;

    /** The ride's membership fields after the action, and every join request it changed. */
    public static final class Result {
        public final Ride ride;
        public final boolean rideChanged;
        public final List<JoinRequest> changedRequests;

        Result(Ride ride, boolean rideChanged, List<JoinRequest> changedRequests) {
            this.ride = ride;
            this.rideChanged = rideChanged;
            this.changedRequests = changedRequests;
        }
    }

    private MembershipTransition() {
    }

    /** One join request per ride and user, so the rules can tell whose request a document is. */
    public static String requestId(String rideId, String userId) {
        return rideId + "_" + userId;
    }

    /**
     * Applies {@code action} to copies of the ride and requests. {@code requests} holds the user's
     * own request (if any), the target request for accept/reject, and the ride's pending requests.
     * Throws RideRuleException with a message for the user when the action isn't allowed.
     */
    public static Result apply(Ride source, List<JoinRequest> requests, String action, String uid,
                               String targetId, String message, String displayName, Timestamp now) {
        if (uid == null) throw new RideRuleException("Please log in first.");
        if (source == null) throw new RideRuleException("Ride not found.");
        if (source.getParticipants() == null || source.getMaxRiders() < 2 || source.getMaxRiders() > 6
                || source.getActivityStatus() == null) {
            throw new RideRuleException("This ride has invalid membership data.");
        }

        Ride ride = copyMembership(source);
        List<JoinRequest> copies = new ArrayList<>();
        for (JoinRequest request : requests) copies.add(copy(request));
        List<JoinRequest> changed = new ArrayList<>();
        JoinRequest own = null;
        JoinRequest target = null;
        for (JoinRequest request : copies) {
            if (uid.equals(request.getUserId())) own = request;
            if (targetId != null && targetId.equals(request.getId())) target = request;
        }
        boolean rideChanged = false;

        switch (action) {
            case REQUEST: {
                requireActive(ride);
                requireBeforeDeparture(ride, now);
                if (uid.equals(ride.getOrganizerId())) throw new RideRuleException("You cannot request your own ride.");
                if (isMember(ride, uid)) throw new RideRuleException("You are already participating in this ride.");
                if (ride.getParticipants().size() >= ride.getMaxRiders()) throw new RideRuleException("This ride is full.");
                if (own != null && own.getStatus() == JoinRequest.Status.REJECTED) {
                    throw new RideRuleException("You were previously rejected from this ride.");
                }
                if (own != null && own.getStatus() == JoinRequest.Status.PENDING) {
                    throw new RideRuleException("You already have a pending request.");
                }
                String text = message == null ? "" : message.trim();
                if (text.length() > 1000) throw new RideRuleException("Use a message of at most 1,000 characters.");
                // A cancelled request may be submitted again; a rejected request may not.
                JoinRequest created = new JoinRequest();
                created.setId(requestId(ride.getId(), uid));
                created.setRideId(ride.getId());
                created.setUserId(uid);
                created.setDisplayName(displayName);
                created.setMessage(text);
                created.setStatus(JoinRequest.Status.PENDING);
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                changed.add(created);
                break;
            }
            case CANCEL:
                if (own == null || own.getStatus() != JoinRequest.Status.PENDING) {
                    throw new RideRuleException("There is no pending request to cancel.");
                }
                setStatus(own, JoinRequest.Status.CANCELLED, null, now, changed);
                break;
            case ACCEPT:
            case REJECT:
                requireOrganizer(ride, uid);
                requireActive(ride);
                if (target == null || target.getStatus() != JoinRequest.Status.PENDING) {
                    throw new RideRuleException("This request is no longer pending.");
                }
                if (action.equals(REJECT)) {
                    setStatus(target, JoinRequest.Status.REJECTED, null, now, changed);
                    break;
                }
                requireBeforeDeparture(ride, now);
                if (ride.getParticipants().size() >= ride.getMaxRiders()) throw new RideRuleException("This ride is already full.");
                if (isMember(ride, target.getUserId())) throw new RideRuleException("This rider is already participating.");
                // Array order is the acceptance order, so the newest rider is always last.
                ride.getParticipants().add(new Participant(target.getUserId(), target.getDisplayName(), now));
                setStatus(target, JoinRequest.Status.ACCEPTED, null, now, changed);
                rideChanged = true;
                break;
            case CANCEL_RIDE:
                requireOrganizer(ride, uid);
                requireActive(ride);
                requireThreeHoursBefore(ride, now,
                        "Cancellation requires at least three hours before the original departure.");
                ride.setActivityStatus(ActivityStatus.CANCELLED);
                rideChanged = true;
                break;
            case LEAVE: {
                requireActive(ride);
                if (!isMember(ride, uid)) throw new RideRuleException("You are not a participant in this ride.");
                requireThreeHoursBefore(ride, now,
                        "You can leave only at least three hours before the original departure.");
                List<Participant> remaining = new ArrayList<>();
                for (Participant participant : ride.getParticipants()) {
                    if (!uid.equals(participant.getUserId())) remaining.add(participant);
                }
                ride.setParticipants(remaining);
                if (uid.equals(ride.getOrganizerId())) {
                    if (remaining.isEmpty()) {
                        ride.setActivityStatus(ActivityStatus.CANCELLED);
                    } else {
                        // The rider who joined most recently becomes the organizer.
                        ride.setOrganizerId(remaining.get(remaining.size() - 1).getUserId());
                    }
                }
                rideChanged = true;
                break;
            }
            default:
                throw new RideRuleException("Unknown membership action.");
        }

        ride.syncParticipantIds();
        ride.setCapacityStatus(ride.isFinished() ? CapacityStatus.NOT_APPLICABLE
                : ride.getParticipants().size() >= ride.getMaxRiders() ? CapacityStatus.FULL : CapacityStatus.OPEN);
        // Filling the last seat or cancelling closes every pending request in the same transaction.
        if (ride.getCapacityStatus() == CapacityStatus.FULL || ride.getActivityStatus() == ActivityStatus.CANCELLED) {
            String reason = ride.getActivityStatus() == ActivityStatus.CANCELLED ? "RIDE_CANCELLED" : "RIDE_FULL";
            for (JoinRequest request : copies) {
                if (request.getStatus() == JoinRequest.Status.PENDING) {
                    setStatus(request, JoinRequest.Status.REJECTED, reason, now, changed);
                }
            }
        }
        return new Result(ride, rideChanged, changed);
    }

    private static void setStatus(JoinRequest request, JoinRequest.Status status, String closedReason,
                                  Timestamp now, List<JoinRequest> changed) {
        request.setStatus(status);
        request.setUpdatedAt(now);
        if (closedReason != null) request.setClosedReason(closedReason);
        if (!changed.contains(request)) changed.add(request);
    }

    private static void requireActive(Ride ride) {
        if (ride.isFinished()) throw new RideRuleException("This ride is cancelled or completed.");
    }

    private static void requireBeforeDeparture(Ride ride, Timestamp now) {
        Timestamp departure = ride.getCurrentDepartureTime();
        if (departure == null || now.compareTo(departure) >= 0) {
            throw new RideRuleException("Requests and approvals stop at departure.");
        }
    }

    // The three-hour cutoff always uses the original departure time, even for delayed rides.
    private static void requireThreeHoursBefore(Ride ride, Timestamp now, String message) {
        Timestamp departure = ride.getDepartureTime();
        if (departure == null
                || departure.toDate().getTime() - now.toDate().getTime() < THREE_HOURS_MILLIS) {
            throw new RideRuleException(message);
        }
    }

    private static void requireOrganizer(Ride ride, String uid) {
        if (!uid.equals(ride.getOrganizerId())) throw new RideRuleException("Only the current organizer can do this.");
    }

    private static boolean isMember(Ride ride, String uid) {
        for (Participant participant : ride.getParticipants()) {
            if (uid.equals(participant.getUserId())) return true;
        }
        return false;
    }

    private static Ride copyMembership(Ride source) {
        Ride ride = new Ride();
        ride.setId(source.getId());
        ride.setDepartureTime(source.getDepartureTime());
        ride.setDelayedDepartureTime(source.getDelayedDepartureTime());
        ride.setMaxRiders(source.getMaxRiders());
        ride.setOrganizerId(source.getOrganizerId());
        ride.setActivityStatus(source.getActivityStatus());
        ride.setCapacityStatus(source.getCapacityStatus());
        ride.setParticipants(new ArrayList<>(source.getParticipants()));
        return ride;
    }

    private static JoinRequest copy(JoinRequest source) {
        JoinRequest request = new JoinRequest();
        request.setId(source.getId());
        request.setRideId(source.getRideId());
        request.setUserId(source.getUserId());
        request.setDisplayName(source.getDisplayName());
        request.setMessage(source.getMessage());
        request.setStatus(source.getStatus());
        request.setClosedReason(source.getClosedReason());
        request.setCreatedAt(source.getCreatedAt());
        request.setUpdatedAt(source.getUpdatedAt());
        return request;
    }
}
