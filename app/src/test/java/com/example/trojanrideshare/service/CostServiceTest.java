package com.example.trojanrideshare.service;

import static org.junit.Assert.*;
import org.junit.Test;

import com.example.trojanrideshare.model.CostShare;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CostServiceTest {

    private final CostService service = new CostService();
    private final List<String> riders =
            Arrays.asList("alice", "bob", "charlie");

    @Test
    public void organizerCanCompleteWithEqualSplit() {
        Map<String, CostShare> shares = service.prepareCompletion(
                "alice", "alice", false, 6050, riders, null);

        assertEquals(3, shares.size());
        assertEquals(2017, shares.get("alice").getAmountCents());
        assertEquals(2017, shares.get("bob").getAmountCents());
        assertEquals(2016, shares.get("charlie").getAmountCents());
        assertFalse(shares.get("alice").isPaid());
    }

    @Test
    public void organizerCanCompleteWithCustomSplit() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 3000L);
        custom.put("bob", 2000L);
        custom.put("charlie", 1050L);

        Map<String, CostShare> shares = service.prepareCompletion(
                "alice", "alice", false, 6050, riders, custom);

        assertEquals(3000, shares.get("alice").getAmountCents());
        assertEquals(2000, shares.get("bob").getAmountCents());
        assertEquals(1050, shares.get("charlie").getAmountCents());
    }

    @Test(expected = IllegalStateException.class)
    public void nonOrganizerCannotCompleteRide() {
        service.prepareCompletion(
                "alice", "bob", false, 6050, riders, null);
    }

    @Test(expected = IllegalStateException.class)
    public void completedRideCannotBeCompletedAgain() {
        service.prepareCompletion(
                "alice", "alice", true, 6050, riders, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroCostIsRejected() {
        service.prepareCompletion(
                "alice", "alice", false, 0, riders, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void mismatchedCustomTotalIsRejected() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 3000L);
        custom.put("bob", 2000L);
        custom.put("charlie", 1000L);

        service.prepareCompletion(
                "alice", "alice", false, 6050, riders, custom);
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateParticipantsAreRejected() {
        service.prepareCompletion(
                "alice", "alice", false, 1000,
                Arrays.asList("alice", "alice"), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingCustomParticipantIsRejected() {
        Map<String, Long> custom = new LinkedHashMap<>();
        custom.put("alice", 4000L);
        custom.put("bob", 2050L);

        service.prepareCompletion(
                "alice", "alice", false, 6050, riders, custom);
    }
}
