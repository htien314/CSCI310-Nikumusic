package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CostTransitionTest {

    private static final long DAY = 24 * 3_600_000L;
    private static final long NOW = 1_000 * DAY;

    private static Timestamp at(long millis) {
        return new Timestamp(new Date(millis));
    }

    private static Ride departedRide(String... members) {
        Ride ride = new Ride();
        ride.setOrganizerId(members[0]);
        ride.setMaxRiders(4);
        ride.setDepartureTime(at(NOW - 1));
        ride.setActivityStatus(ActivityStatus.ON_TIME);
        List<Participant> participants = new ArrayList<>();
        for (String member : members) participants.add(new Participant(member, member, at(NOW - DAY)));
        ride.setParticipants(participants);
        return ride;
    }

    private static Ride completedRide(long completedAt) {
        Ride ride = departedRide("alice", "bob");
        ride.setActivityStatus(ActivityStatus.COMPLETED);
        ride.setCompletedAt(at(completedAt));
        Map<String, CostShare> shares = new LinkedHashMap<>();
        shares.put("alice", new CostShare("alice", 500));
        shares.put("bob", new CostShare("bob", 500));
        ride.setCostShares(shares);
        return ride;
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
    public void complete_splitsTenDollarsAmongThreeIntoCents() {
        Map<String, CostShare> shares = CostTransition.complete(departedRide("alice", "bob", "carol"),
                "alice", 1000, null, at(NOW));

        assertEquals(334, shares.get("alice").getAmountCents());
        assertEquals(333, shares.get("bob").getAmountCents());
        assertEquals(333, shares.get("carol").getAmountCents());
    }

    @Test
    public void complete_soloOrganizerPaysEverything() {
        assertEquals(1250, CostTransition.complete(departedRide("alice"), "alice", 1250, null, at(NOW))
                .get("alice").getAmountCents());
    }

    @Test
    public void complete_acceptsZeroCustomShareButRejectsMismatchedTotal() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 1000L);
        custom.put("bob", 0L);
        assertEquals(0, CostTransition.complete(departedRide("alice", "bob"), "alice", 1000, custom, at(NOW))
                .get("bob").getAmountCents());

        custom.put("bob", 1L);
        assertRejected(() -> CostTransition.complete(departedRide("alice", "bob"), "alice", 1000, custom, at(NOW)),
                "Provide a share of $0.00 or more for every participant, totaling the actual cost.");
    }

    @Test
    public void complete_onlyOrganizerAfterDeparture() {
        assertRejected(() -> CostTransition.complete(departedRide("alice", "bob"), "bob", 1000, null, at(NOW)),
                "Only the organizer can complete this ride.");
        assertRejected(() -> CostTransition.complete(departedRide("alice"), "alice", 1000, null, at(NOW - 2)),
                "The ride cannot be completed before departure.");
        assertRejected(() -> CostTransition.complete(departedRide("alice"), "alice", 0, null, at(NOW)),
                "Enter a positive total in whole cents.");
    }

    @Test
    public void paid_onlyForOwnShareAndOnce() {
        Ride ride = completedRide(NOW - DAY);
        Map<String, CostShare> shares = CostTransition.updateShare(ride, CostTransition.PAID, "bob", "bob", at(NOW));
        assertTrue(shares.get("bob").isPaid());
        assertFalse(ride.getCostShares().get("bob").isPaid());

        assertRejected(() -> CostTransition.updateShare(ride, CostTransition.PAID, "alice", "bob", at(NOW)),
                "You may mark only your own share Paid.");
        ride.setCostShares(shares);
        assertRejected(() -> CostTransition.updateShare(ride, CostTransition.PAID, "bob", "bob", at(NOW)),
                "This share is already Paid.");
    }

    @Test
    public void flag_onlyByOrganizerAfterSevenDays() {
        assertRejected(() -> CostTransition.updateShare(completedRide(NOW - 6 * DAY), CostTransition.FLAG, "alice", "bob", at(NOW)),
                "Flagging is available seven days after cost recording.");
        assertRejected(() -> CostTransition.updateShare(completedRide(NOW - 8 * DAY), CostTransition.FLAG, "bob", "bob", at(NOW)),
                "Only the organizer may flag unpaid riders.");
        assertTrue(CostTransition.updateShare(completedRide(NOW - 8 * DAY), CostTransition.FLAG, "alice", "bob", at(NOW))
                .get("bob").isFlagged());
    }
}
