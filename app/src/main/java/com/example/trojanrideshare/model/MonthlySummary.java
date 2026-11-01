package com.example.trojanrideshare.model;

public class MonthlySummary {

    private final int rideCount;
    private final long totalCostCents;
    private final long myShareCents;
    private final long paidCents;
    private final long unpaidCents;

    public MonthlySummary(
            int rideCount,
            long totalCostCents,
            long myShareCents,
            long paidCents,
            long unpaidCents) {

        this.rideCount = rideCount;
        this.totalCostCents = totalCostCents;
        this.myShareCents = myShareCents;
        this.paidCents = paidCents;
        this.unpaidCents = unpaidCents;
    }

    public int getRideCount() {
        return rideCount;
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
