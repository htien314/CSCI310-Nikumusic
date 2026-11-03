package com.example.trojanrideshare.service;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CostCalculatorTest {

    private final List<String> riders =
            Arrays.asList("alice", "bob", "charlie");

    @Test
    public void equalSplitDistributesCents() {
        Map<String, Long> shares =
                CostCalculator.splitEqual(6050, riders);

        assertEquals(Long.valueOf(2017), shares.get("alice"));
        assertEquals(Long.valueOf(2017), shares.get("bob"));
        assertEquals(Long.valueOf(2016), shares.get("charlie"));
    }

    @Test
    public void equalSplitPreservesTotal() {
        Map<String, Long> shares =
                CostCalculator.splitEqual(1001, riders);

        long sum = 0;
        for (long amount : shares.values()) {
            sum += amount;
        }

        assertEquals(1001, sum);
    }

    @Test
    public void customSplitAcceptsExactTotal() {
        Map<String, Long> shares = new LinkedHashMap<>();
        shares.put("alice", 3000L);
        shares.put("bob", 2000L);
        shares.put("charlie", 1050L);

        CostCalculator.validateCustom(6050, riders, shares);
    }

    @Test(expected = IllegalArgumentException.class)
    public void customSplitRejectsWrongTotal() {
        Map<String, Long> shares = new LinkedHashMap<>();
        shares.put("alice", 3000L);
        shares.put("bob", 2000L);
        shares.put("charlie", 1000L);

        CostCalculator.validateCustom(6050, riders, shares);
    }

    @Test(expected = IllegalArgumentException.class)
    public void customSplitRejectsNegativeAmounts() {
        Map<String, Long> shares = new LinkedHashMap<>();
        shares.put("alice", 7000L);
        shares.put("bob", -950L);
        shares.put("charlie", 0L);

        CostCalculator.validateCustom(6050, riders, shares);
    }

    @Test(expected = IllegalArgumentException.class)
    public void equalSplitRejectsZeroCost() {
        CostCalculator.splitEqual(0, riders);
    }

    @Test(expected = IllegalArgumentException.class)
    public void equalSplitRejectsDuplicateRiders() {
        CostCalculator.splitEqual(
                1000, Arrays.asList("alice", "alice"));
    }
}

