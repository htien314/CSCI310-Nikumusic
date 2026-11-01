package com.example.trojanrideshare.service;

import static org.junit.Assert.*;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class PaymentTrackingTest {

    private FakeRideRepository repository;
    private CostService service;
    private Ride ride;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        service = new CostService(repository, null, () -> 1000L);

        ride = new Ride();
        ride.setId("ride1");
        ride.setOrganizerId("alice");
        ride.setActivityStatus(ActivityStatus.COMPLETED);

        Map<String, CostShare> shares = new LinkedHashMap<>();
        shares.put("alice", new CostShare("alice", 3000));
        shares.put("bob", new CostShare("bob", 3000));
        ride.setCostShares(shares);

        repository.rides.put("ride1", ride);
    }

    @Test
    public void organizerCanMarkRiderPaid() {
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "alice", "bob", callback);

        assertTrue(callback.success);
        assertNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isPaid());
        assertFalse(ride.getCostShares().get("alice").isPaid());
    }

    @Test
    public void nonOrganizerCannotRecordPayment() {
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "bob", "bob", callback);

        assertNotNull(callback.error);
        assertFalse(ride.getCostShares().get("bob").isPaid());
    }

    @Test
    public void incompleteRideCannotRecordPayment() {
        ride.setActivityStatus(ActivityStatus.ON_TIME);
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "alice", "bob", callback);

        assertNotNull(callback.error);
        assertFalse(ride.getCostShares().get("bob").isPaid());
    }

    @Test
    public void unknownRiderIsRejected() {
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "alice", "charlie", callback);

        assertNotNull(callback.error);
    }

    @Test
    public void duplicatePaymentIsRejected() {
        ride.getCostShares().get("bob").markPaid();
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "alice", "bob", callback);

        assertNotNull(callback.error);
        assertTrue(ride.getCostShares().get("bob").isPaid());
    }

    @Test
    public void recordingPaymentClearsOverdueFlag() {
        ride.getCostShares().get("bob").setFlagged(true);
        TestCallback callback = new TestCallback();

        service.markRiderPaid("ride1", "alice", "bob", callback);

        assertTrue(callback.success);
        assertTrue(ride.getCostShares().get("bob").isPaid());
        assertFalse(ride.getCostShares().get("bob").isFlagged());
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
