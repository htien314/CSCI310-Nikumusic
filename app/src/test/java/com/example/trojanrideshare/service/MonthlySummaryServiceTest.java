package com.example.trojanrideshare.service;

import static org.junit.Assert.*;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.MonthlySummary;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class MonthlySummaryServiceTest {

    private MonthlySummaryService service;

    @Before
    public void setUp() {
        service = new MonthlySummaryService();
    }

    @Test
    public void completedRideCountsTowardMonth() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, false);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(1, result.getRideCount());
        assertEquals(6000, result.getTotalCostCents());
        assertEquals(2000, result.getMyShareCents());
        assertEquals(0, result.getPaidCents());
        assertEquals(2000, result.getUnpaidCents());
    }

    @Test
    public void paidShareCountsAsPaid() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, true);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(2000, result.getPaidCents());
        assertEquals(0, result.getUnpaidCents());
    }

    @Test
    public void differentMonthIsExcluded() {
        Ride ride = completedRide(
                2026, 10, 31, 6000, 2000, false);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(0, result.getRideCount());
        assertEquals(0, result.getMyShareCents());
    }

    @Test
    public void cancelledRideIsExcluded() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, false);
        ride.setActivityStatus(ActivityStatus.CANCELLED);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(0, result.getRideCount());
        assertEquals(0, result.getOrganizedCount());
        assertEquals(0, result.getJoinedCount());
    }

    @Test
    public void unrelatedRiderIsExcluded() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, false);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "charlie",
                YearMonth.of(2026, 11));

        assertEquals(0, result.getRideCount());
        assertEquals(0, result.getMyShareCents());
    }

    @Test
    public void multipleRidesAreSummed() {
        Ride first = completedRide(
                2026, 11, 5, 6000, 2000, true);
        Ride second = completedRide(
                2026, 11, 15, 9000, 3000, false);

        MonthlySummary result = service.calculate(
                Arrays.asList(first, second),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(2, result.getRideCount());
        assertEquals(15000, result.getTotalCostCents());
        assertEquals(5000, result.getMyShareCents());
        assertEquals(2000, result.getPaidCents());
        assertEquals(3000, result.getUnpaidCents());
    }

    @Test
    public void monthBoundaryUsesDisplayZone() {
        Ride ride = completedRide(
                2026, 10, 31, 6000, 2000, false);

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 10));

        assertEquals(1, result.getRideCount());
    }

    // A ride organized by Alice counts as organized, not joined.
    @Test
    public void organizedRideIsCounted() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, false);
        ride.setOrganizerId("alice");

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(1, result.getRideCount());
        assertEquals(1, result.getOrganizedCount());
        assertEquals(0, result.getJoinedCount());
    }

    // A ride organized by someone else counts as joined.
    @Test
    public void joinedRideIsCounted() {
        Ride ride = completedRide(
                2026, 11, 10, 6000, 2000, false);
        ride.setOrganizerId("bob");

        MonthlySummary result = service.calculate(
                Collections.singletonList(ride),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(1, result.getRideCount());
        assertEquals(0, result.getOrganizedCount());
        assertEquals(1, result.getJoinedCount());
    }

    // Organized and joined totals should add up across rides.
    @Test
    public void organizedAndJoinedRidesAreSummed() {
        Ride first = completedRide(
                2026, 11, 5, 6000, 2000, true);
        first.setOrganizerId("alice");

        Ride second = completedRide(
                2026, 11, 10, 9000, 3000, false);
        second.setOrganizerId("bob");

        Ride third = completedRide(
                2026, 11, 15, 5000, 1000, false);
        third.setOrganizerId("alice");

        MonthlySummary result = service.calculate(
                Arrays.asList(first, second, third),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(3, result.getRideCount());
        assertEquals(2, result.getOrganizedCount());
        assertEquals(1, result.getJoinedCount());

        assertEquals(20000, result.getTotalCostCents());
        assertEquals(6000, result.getMyShareCents());
        assertEquals(2000, result.getPaidCents());
        assertEquals(4000, result.getUnpaidCents());
    }

    // A month without rides has zero totals.
    @Test
    public void emptyMonthHasZeroTotals() {
        MonthlySummary result = service.calculate(
                Collections.emptyList(),
                "alice",
                YearMonth.of(2026, 11));

        assertEquals(0, result.getRideCount());
        assertEquals(0, result.getOrganizedCount());
        assertEquals(0, result.getJoinedCount());
        assertEquals(0, result.getTotalCostCents());
        assertEquals(0, result.getMyShareCents());
        assertEquals(0, result.getPaidCents());
        assertEquals(0, result.getUnpaidCents());
    }

    // Missing input must not silently produce a summary.
    @Test
    public void missingUserIsRejected() {
        try {
            service.calculate(
                    Collections.emptyList(),
                    "",
                    YearMonth.of(2026, 11));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Correct behavior.
        }
    }

    // Build a completed ride with Alice's cost share.
    private Ride completedRide(
            int year, int month, int day,
            long totalCents, long shareCents, boolean paid) {

        Ride ride = new Ride();
        ride.setActivityStatus(ActivityStatus.COMPLETED);
        ride.setActualCostCents(totalCents);

        ZonedDateTime completed = ZonedDateTime.of(
                year, month, day, 12, 0, 0, 0,
                Constants.DISPLAY_ZONE);

        ride.setCompletedAt(
                new Timestamp(Date.from(completed.toInstant())));

        CostShare share = new CostShare("alice", shareCents);

        if (paid) {
            share.markPaid();
        }

        Map<String, CostShare> shares = new LinkedHashMap<>();
        shares.put("alice", share);
        ride.setCostShares(shares);

        return ride;
    }
}
