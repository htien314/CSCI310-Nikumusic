package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.MonthlySummary;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Constants;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public final class MonthlySummaryService {

    // Calculate the user's costs for a selected month.
    public MonthlySummary calculate(
            List<Ride> rides,
            String userId,
            YearMonth month) {

        if (rides == null || userId == null
                || userId.trim().isEmpty() || month == null) {
            throw new IllegalArgumentException(
                    "Rides, user, and month are required.");
        }

        int rideCount = 0;
        long totalCost = 0;
        long myShare = 0;
        long paid = 0;
        long unpaid = 0;

        for (Ride ride : rides) {
            if (ride == null
                    || ride.getActivityStatus() != ActivityStatus.COMPLETED
                    || ride.getCompletedAt() == null
                    || ride.getActualCostCents() == null
                    || ride.getCostShares() == null) {
                continue;
            }

            YearMonth rideMonth = YearMonth.from(
                    ride.getCompletedAt().toDate().toInstant()
                            .atZone(Constants.DISPLAY_ZONE));

            if (!rideMonth.equals(month)) {
                continue;
            }

            Map<String, CostShare> shares = ride.getCostShares();
            CostShare mine = shares.get(userId);

            if (mine == null) {
                continue;
            }

            rideCount++;
            totalCost += ride.getActualCostCents();
            myShare += mine.getAmountCents();

            if (mine.isPaid()) {
                paid += mine.getAmountCents();
            } else {
                unpaid += mine.getAmountCents();
            }
        }

        return new MonthlySummary(
                rideCount, totalCost, myShare, paid, unpaid);
    }
}

