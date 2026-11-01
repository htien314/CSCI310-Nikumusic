package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.CostShare;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CostService {

    // Prepare shares for a valid organizer-led completion.
    public Map<String, CostShare> prepareCompletion(
            String organizerId,
            String currentUserId,
            boolean completed,
            long totalCents,
            List<String> participantIds,
            Map<String, Long> customShares) {

        if (organizerId == null
                || !organizerId.equals(currentUserId)) {
            throw new IllegalStateException(
                    "Only the organizer can complete this ride.");
        }

        if (completed) {
            throw new IllegalStateException(
                    "This ride is already completed.");
        }

        Map<String, Long> amounts;

        if (customShares == null) {
            amounts = CostCalculator.splitEqual(
                    totalCents, participantIds);
        } else {
            CostCalculator.validateCustom(
                    totalCents, participantIds, customShares);
            amounts = customShares;
        }

        Map<String, CostShare> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            result.put(entry.getKey(), new CostShare(
                    entry.getKey(), entry.getValue()));
        }

        return result;
    }
}
