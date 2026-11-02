package com.example.trojanrideshare.service;

import static org.junit.Assert.*;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class RideCompletionTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final String RIDE_ID = "ride1";

    private FakeRideRepository repository;
    private CostService costService;
    private Ride ride;
    private TestCallback callback;

    @Before
    public void setUp() {
        Clock clock = () -> NOW;

        repository = new FakeRideRepository();
        RideService rideService = new RideService(repository, clock);
        costService = new CostService(repository, rideService, clock);

        ride = new Ride();
        ride.setId(RIDE_ID);
        ride.setOrganizerId("alice");
        ride.setDepartureTime(timestamp(NOW - 60_000));
        ride.setActivityStatus(ActivityStatus.ON_TIME);
        ride.setMaxRiders(4);
        ride.setParticipants(Arrays.asList(
                new Participant("alice", "Alice", timestamp(NOW - 120_000)),
                new Participant("bob", "Bob", timestamp(NOW - 110_000)),
                new Participant("charlie", "Charlie", timestamp(NOW - 100_000))
        ));
        ride.syncCapacityStatus();

        repository.rides.put(RIDE_ID, ride);
        callback = new TestCallback();
    }

    // Organizer can complete a departed ride with equal shares.
    @Test
    public void organizerCompletesRide() {
        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertTrue(callback.success);
        assertNull(callback.error);
        assertEquals(ActivityStatus.COMPLETED, ride.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, ride.getCapacityStatus());
        assertEquals(Long.valueOf(9000L), ride.getActualCostCents());
        assertEquals(timestamp(NOW), ride.getCompletedAt());

        assertEquals(3, ride.getCostShares().size());
        assertEquals(3000L, ride.getCostShares().get("alice").getAmountCents());
        assertEquals(3000L, ride.getCostShares().get("bob").getAmountCents());
        assertEquals(3000L, ride.getCostShares().get("charlie").getAmountCents());

        for (CostShare share : ride.getCostShares().values()) {
            assertFalse(share.isPaid());
            assertFalse(share.isFlagged());
        }
    }

    // Only the organizer may complete the ride.
    @Test
    public void nonOrganizerCannotComplete() {
        costService.completeRide(
                RIDE_ID, "bob", 9000L, null, callback);

        assertRejectedWithoutCompletion();
    }

    // Completion cannot happen before departure.
    @Test
    public void cannotCompleteBeforeDeparture() {
        ride.setDepartureTime(timestamp(NOW + 60_000));

        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertRejectedWithoutCompletion();
    }

    // A completed ride cannot be completed again.
    @Test
    public void cannotCompleteTwice() {
        ride.setActivityStatus(ActivityStatus.COMPLETED);

        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertNull(ride.getActualCostCents());
    }

    // Cancelled rides cannot be completed.
    @Test
    public void cannotCompleteCancelledRide() {
        ride.setActivityStatus(ActivityStatus.CANCELLED);

        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertNull(ride.getActualCostCents());
    }

    // Custom shares must be saved exactly as entered.
    @Test
    public void validCustomSplit() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 4000L);
        custom.put("bob", 3000L);
        custom.put("charlie", 2000L);

        costService.completeRide(
                RIDE_ID, "alice", 9000L, custom, callback);

        assertTrue(callback.success);
        assertEquals(4000L, ride.getCostShares().get("alice").getAmountCents());
        assertEquals(3000L, ride.getCostShares().get("bob").getAmountCents());
        assertEquals(2000L, ride.getCostShares().get("charlie").getAmountCents());
    }

    // The total of custom shares must match the ride cost.
    @Test
    public void invalidCustomSplitIsRejected() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 4000L);
        custom.put("bob", 3000L);
        custom.put("charlie", 1000L);

        costService.completeRide(
                RIDE_ID, "alice", 9000L, custom, callback);

        assertRejectedWithoutCompletion();
    }

    // Invalid participant IDs must not be accepted.
    @Test
    public void invalidParticipantIsRejected() {
        ride.getParticipants().get(1).setUserId("");

        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertRejectedWithoutCompletion();
    }

    // A missing ride should return an error.
    @Test
    public void missingRideIsRejected() {
        costService.completeRide(
                "missing", "alice", 9000L, null, callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
    }

    // A valid completion must preserve the original estimate.
    @Test
    public void expectedCostIsPreserved() {
        ride.setExpectedCostCents(12000L);

        costService.completeRide(
                RIDE_ID, "alice", 9000L, null, callback);

        assertTrue(callback.success);
        assertEquals(12000L, ride.getExpectedCostCents());
        assertEquals(Long.valueOf(9000L), ride.getActualCostCents());
    }

    private void assertRejectedWithoutCompletion() {
        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertNull(ride.getActualCostCents());
        assertNull(ride.getCostShares());
        assertNull(ride.getCompletedAt());
    }

    private static Timestamp timestamp(long millis) {
        return new Timestamp(new Date(millis));
    }

    private static class TestCallback implements Callback<Ride> {

        boolean success;
        Exception error;

        @Override
        public void onSuccess(Ride result) {
            success = true;
        }

        @Override
        public void onError(Exception exception) {
            error = exception;
        }
    }
}
