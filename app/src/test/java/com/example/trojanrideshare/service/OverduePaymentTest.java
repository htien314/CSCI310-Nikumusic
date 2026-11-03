package com.example.trojanrideshare.service;

import static org.junit.Assert.*;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OverduePaymentTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private FakeRideRepository repository;
    private CostService service;
    private Ride ride;

    @Before
    public void setUp() {
        Clock clock = () -> NOW;
        repository = new FakeRideRepository();
        service = new CostService(repository, null, clock);

        ride = new Ride();
        ride.setId("ride1");
        ride.setOrganizerId("alice");
        ride.setActivityStatus(ActivityStatus.COMPLETED);
        ride.setCompletedAt(
                new Timestamp(new Date(NOW - 8 * DAY)));

        Map<String, CostShare> shares = new LinkedHashMap<>();
        shares.put("alice", new CostShare("alice", 3000));
        shares.put("bob", new CostShare("bob", 3000));
        ride.setCostShares(shares);

        repository.rides.put("ride1", ride);
    }

    @Test
    public void unpaidShareAfterEightDaysIsOverdue() {
        assertTrue(service.isPaymentOverdue(ride, "bob"));
    }

    @Test
    public void unpaidShareBeforeSevenDaysIsNotOverdue() {
        ride.setCompletedAt(
                new Timestamp(new Date(NOW - 6 * DAY)));

        assertFalse(service.isPaymentOverdue(ride, "bob"));
    }

    @Test
    public void unpaidShareAtExactlySevenDaysIsOverdue() {
        ride.setCompletedAt(
                new Timestamp(new Date(NOW - 7 * DAY)));

        assertTrue(service.isPaymentOverdue(ride, "bob"));
    }

    @Test
    public void paidShareIsNotOverdue() {
        ride.getCostShares().get("bob").markPaid();

        assertFalse(service.isPaymentOverdue(ride, "bob"));
    }

    @Test
    public void missingRiderIsNotOverdue() {
        assertFalse(service.isPaymentOverdue(ride, "missing"));
    }

    @Test
    public void incompleteRideIsNotOverdue() {
        ride.setActivityStatus(ActivityStatus.ON_TIME);

        assertFalse(service.isPaymentOverdue(ride, "bob"));
    }

    @Test
    public void organizerCanFlagOverdueUnpaidRider() {
        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertTrue(callback.success);
        assertNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isFlagged());
        assertFalse(ride.getCostShares().get("alice").isFlagged());
    }

    @Test
    public void nonOrganizerCannotFlagRider() {
        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "bob", "bob", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertFalse(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void organizerCannotFlagBeforeSevenDays() {
        ride.setCompletedAt(
                new Timestamp(new Date(NOW - 6 * DAY)));

        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertFalse(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void organizerCanFlagAtExactlySevenDays() {
        ride.setCompletedAt(
                new Timestamp(new Date(NOW - 7 * DAY)));

        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertTrue(callback.success);
        assertNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void organizerCannotFlagPaidRider() {
        ride.getCostShares().get("bob").markPaid();

        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertFalse(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void organizerCannotFlagIncompleteRide() {
        ride.setActivityStatus(ActivityStatus.ON_TIME);

        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
    }

    @Test
    public void duplicateFlagIsRejected() {
        ride.getCostShares().get("bob").setFlagged(true);

        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "bob", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void flaggedRiderCanMarkOwnPaymentPaid() {
        ride.getCostShares().get("bob").setFlagged(true);

        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "bob", "bob", callback);

        assertTrue(callback.success);
        assertNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isPaid());
        assertFalse(ride.getCostShares().get("bob").isFlagged());
    }

    @Test
    public void unknownRiderCannotBeFlagged() {
        TestCallback callback = new TestCallback();

        service.flagRider("ride1", "alice", "charlie", callback);

        assertFalse(callback.success);
        assertNotNull(callback.error);
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
