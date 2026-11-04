package com.example.trojanrideshare.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CostCalculator {

    private CostCalculator() {}

    // Split the total evenly, distributing leftover cents.
    public static Map<String, Long> splitEqual(
            long totalCents, List<String> riderIds) {

        validateRiders(totalCents, riderIds);

        long base = totalCents / riderIds.size();
        long remainder = totalCents % riderIds.size();

        Map<String, Long> shares = new LinkedHashMap<>();

        for (int i = 0; i < riderIds.size(); i++) {
            shares.put(riderIds.get(i),
                    base + (i < remainder ? 1 : 0));
        }
        return shares;
    }

    // Require custom shares to add up to the exact total.
    public static void validateCustom(
            long totalCents,
            List<String> riderIds,
            Map<String, Long> shares) {

        validateRiders(totalCents, riderIds);

        if (shares == null ||
                !shares.keySet().equals(new HashSet<>(riderIds))) {
            throw new IllegalArgumentException(
                    "Provide one share for every rider.");
        }

        long sum = 0;
        for (Long amount : shares.values()) {
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException(
                        "Shares cannot be negative.");
            }
            sum = Math.addExact(sum, amount);
        }

        if (sum != totalCents) {
            throw new IllegalArgumentException(
                    "Shares must equal the total cost.");
        }
    }

    private static void validateRiders(
            long totalCents, List<String> riderIds) {

        if (totalCents <= 0) {
            throw new IllegalArgumentException(
                    "Total cost must be positive.");
        }

        if (riderIds == null || riderIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one rider is required.");
        }

        Set<String> uniqueIds = new HashSet<>();
        for (String id : riderIds) {
            if (id == null || id.trim().isEmpty()
                    || !uniqueIds.add(id)) {
                throw new IllegalArgumentException(
                        "Rider IDs must be unique and valid.");
            }
        }
    }
}
