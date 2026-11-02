package com.example.trojanrideshare.model;

public class MonthlySummary {

    private final int rideCount;
    private final int organizedCount;
    private final int joinedCount;

    private final long totalCostCents;
    private final long myShareCents;
    private final long paidCents;
    private final long unpaidCents;

    // Keep the original constructor for existing tests.
    public MonthlySummary(
            int rideCount,
            long totalCostCents,
            long myShareCents,
            long paidCents,
            long unpaidCents) {

        this(rideCount, 0, 0, totalCostCents,
                myShareCents, paidCents, unpaidCents);
    }

    // Full monthly summary including organized and joined rides.
    public MonthlySummary(
            int rideCount,
            int organizedCount,
            int joinedCount,
            long totalCostCents,
            long myShareCents,
            long paidCents,
            long unpaidCents) {

        this.rideCount = rideCount;
        this.organizedCount = organizedCount;
        this.joinedCount = joinedCount;
        this.totalCostCents = totalCostCents;
        this.myShareCents = myShareCents;
        this.paidCents = paidCents;
        this.unpaidCents = unpaidCents;
    }

    public int getRideCount() {
        return rideCount;
    }

    public int getOrganizedCount() {
        return organizedCount;
    }

    public int getJoinedCount() {
        return joinedCount;
    }

    public long getTotalCostCents() {
        return totalCostCents;
    }

    public long getMyShareCents() {
        return myShareCents;
    }

    public long getPaidCents() {
        return paidCents;
    }

    public long getUnpaidCents() {
        return unpaidCents;
    }
}
