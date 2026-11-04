package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MembershipTransitionTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_000 * HOUR;

    private static Timestamp at(long millis) {
        return new Timestamp(new java.util.Date(millis));
    }

    private static Ride ride(int maxRiders, long departure, String... members) {
        Ride ride = new Ride();
        ride.setId("ride");
        ride.setOrganizerId(members[0]);
        ride.setMaxRiders(maxRiders);
        ride.setDepartureTime(at(departure));
        ride.setActivityStatus(ActivityStatus.ON_TIME);
        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < members.length; i++) participants.add(new Participant(members[i], members[i], at(NOW - 10 * HOUR + i)));
        ride.setParticipants(participants);
        ride.syncCapacityStatus();
        return ride;
    }

    private static JoinRequest request(String uid, JoinRequest.Status status) {
        JoinRequest request = new JoinRequest();
        request.setId(MembershipTransition.requestId("ride", uid));
        request.setRideId("ride");
        request.setUserId(uid);
        request.setDisplayName(uid);
        request.setStatus(status);
        return request;
    }

    private static MembershipTransition.Result apply(Ride ride, List<JoinRequest> requests, String action,
                                                     String uid, String targetId) {
        return MembershipTransition.apply(ride, requests, action, uid, targetId, "hi", uid, at(NOW));
    }

    private static void assertRejected(Runnable action, String message) {
        try {
            action.run();
            fail("Expected: " + message);
        } catch (RideRuleException e) {
            assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void request_createsPendingRequestWithRideAndUserId() {
        MembershipTransition.Result result = apply(ride(3, NOW + 10 * HOUR, "alice"),
                Collections.emptyList(), MembershipTransition.REQUEST, "bob", null);

        assertFalse(result.rideChanged);
        JoinRequest created = result.changedRequests.get(0);
        assertEquals("ride_bob", created.getId());
        assertEquals(JoinRequest.Status.PENDING, created.getStatus());
        assertEquals("hi", created.getMessage());
    }

    @Test
    public void request_blockedForOwnFullJoinedRejectedAndDepartedRides() {
        Ride open = ride(3, NOW + 10 * HOUR, "alice");
        assertRejected(() -> apply(open, Collections.emptyList(), MembershipTransition.REQUEST, "alice", null),
                "You cannot request your own ride.");
        Ride full = ride(2, NOW + 10 * HOUR, "alice", "carol");
        assertRejected(() -> apply(full, Collections.emptyList(), MembershipTransition.REQUEST, "bob", null),
                "This ride is full.");
        assertRejected(() -> apply(full, Collections.emptyList(), MembershipTransition.REQUEST, "carol", null),
                "You are already participating in this ride.");
        assertRejected(() -> apply(open, Collections.singletonList(request("bob", JoinRequest.Status.REJECTED)),
                MembershipTransition.REQUEST, "bob", null), "You were previously rejected from this ride.");
        Ride departed = ride(3, NOW - HOUR, "alice");
        assertRejected(() -> apply(departed, Collections.emptyList(), MembershipTransition.REQUEST, "bob", null),
                "Requests and approvals stop at departure.");
    }

    @Test
    public void accept_appendsRiderAndClosesOtherRequestsWhenFull() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice", "dan");
        List<JoinRequest> requests = Arrays.asList(request("bob", JoinRequest.Status.PENDING),
                request("carol", JoinRequest.Status.PENDING));

        MembershipTransition.Result result = apply(ride, requests, MembershipTransition.ACCEPT, "alice", "ride_bob");

        assertEquals(Arrays.asList("alice", "dan", "bob"), result.ride.getParticipantIds());
        assertEquals(CapacityStatus.FULL, result.ride.getCapacityStatus());
        assertEquals(2, result.changedRequests.size());
        assertEquals(JoinRequest.Status.ACCEPTED, result.changedRequests.get(0).getStatus());
        assertEquals("RIDE_FULL", result.changedRequests.get(1).getClosedReason());
        // The caller's ride is untouched.
        assertEquals(2, ride.getParticipants().size());
    }

    @Test
    public void accept_onlyByOrganizer() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice");
        assertRejected(() -> apply(ride, Collections.singletonList(request("bob", JoinRequest.Status.PENDING)),
                MembershipTransition.ACCEPT, "bob", "ride_bob"), "Only the current organizer can do this.");
    }

    @Test
    public void leave_riderKeepsOwnerAndStatus() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice", "bob");
        ride.setActivityStatus(ActivityStatus.DELAYED);

        MembershipTransition.Result result = apply(ride, Collections.emptyList(), MembershipTransition.LEAVE, "bob", null);

        assertEquals("alice", result.ride.getOrganizerId());
        assertEquals(ActivityStatus.DELAYED, result.ride.getActivityStatus());
        assertEquals(CapacityStatus.OPEN, result.ride.getCapacityStatus());
    }

    @Test
    public void leave_blockedWithinThreeHoursOfOriginalDeparture() {
        Ride ride = ride(3, NOW + 3 * HOUR - 1, "alice", "bob");
        assertRejected(() -> apply(ride, Collections.emptyList(), MembershipTransition.LEAVE, "bob", null),
                "You can leave only at least three hours before the original departure.");
        Ride exactly = ride(3, NOW + 3 * HOUR, "alice", "bob");
        apply(exactly, Collections.emptyList(), MembershipTransition.LEAVE, "bob", null);
    }

    @Test
    public void leave_organizerHandsRideToLatestJoinedRider() {
        Ride ride = ride(4, NOW + 10 * HOUR, "alice", "bob", "carol");

        MembershipTransition.Result result = apply(ride, Collections.emptyList(), MembershipTransition.LEAVE, "alice", null);

        assertEquals("carol", result.ride.getOrganizerId());
        assertEquals(Arrays.asList("bob", "carol"), result.ride.getParticipantIds());
    }

    @Test
    public void leave_soloOrganizerCancelsRideAndClosesPendingRequests() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice");

        MembershipTransition.Result result = apply(ride, Collections.singletonList(request("bob", JoinRequest.Status.PENDING)),
                MembershipTransition.LEAVE, "alice", null);

        assertEquals(ActivityStatus.CANCELLED, result.ride.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, result.ride.getCapacityStatus());
        assertEquals("RIDE_CANCELLED", result.changedRequests.get(0).getClosedReason());
    }

    @Test
    public void cancelAndReject_updateRequestStatus() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice");
        assertEquals(JoinRequest.Status.CANCELLED, apply(ride, Collections.singletonList(request("bob", JoinRequest.Status.PENDING)),
                MembershipTransition.CANCEL, "bob", null).changedRequests.get(0).getStatus());
        assertEquals(JoinRequest.Status.REJECTED, apply(ride, Collections.singletonList(request("bob", JoinRequest.Status.PENDING)),
                MembershipTransition.REJECT, "alice", "ride_bob").changedRequests.get(0).getStatus());
    }

    @Test
    public void finishedRidesCannotBeJoinedOrLeft() {
        Ride ride = ride(3, NOW + 10 * HOUR, "alice", "bob");
        ride.setActivityStatus(ActivityStatus.COMPLETED);
        assertRejected(() -> apply(ride, Collections.emptyList(), MembershipTransition.LEAVE, "bob", null),
                "This ride is cancelled or completed.");
        assertRejected(() -> apply(ride, Collections.emptyList(), MembershipTransition.REQUEST, "carol", null),
                "This ride is cancelled or completed.");
    }
}
