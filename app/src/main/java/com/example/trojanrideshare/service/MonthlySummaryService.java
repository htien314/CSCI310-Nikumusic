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

    // Calculate the user's completed rides and costs for a month.
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
        int organizedCount = 0;
        int joinedCount = 0;

        long totalCost = 0;
        long myShare = 0;
        long paid = 0;
        long unpaid = 0;

        for (Ride ride : rides) {

            // Only include completed rides with saved cost information.
            if (ride == null
                    || ride.getActivityStatus() != ActivityStatus.COMPLETED
                    || ride.getCompletedAt() == null
                    || ride.getActualCostCents() == null
                    || ride.getCostShares() == null) {
                continue;
            }

            // Use the same Los Angeles display zone as the rest of the app.
            YearMonth rideMonth = YearMonth.from(
                    ride.getCompletedAt().toDate().toInstant()
                            .atZone(Constants.DISPLAY_ZONE));

            if (!rideMonth.equals(month)) {
                continue;
            }

            Map<String, CostShare> shares = ride.getCostShares();
            CostShare mine = shares.get(userId);

            // Only include rides in which the user participated.
            if (mine == null) {
                continue;
            }

            rideCount++;

            if (ride.isOrganizer(userId)) {
                organizedCount++;
            } else {
                joinedCount++;
            }

            totalCost += ride.getActualCostCents();
            myShare += mine.getAmountCents();

            if (mine.isPaid()) {
                paid += mine.getAmountCents();
            } else {
                unpaid += mine.getAmountCents();
            }
        }

        return new MonthlySummary(
                rideCount,
                organizedCount,
                joinedCount,
                totalCost,
                myShare,
                paid,
                unpaid);
    }
}
