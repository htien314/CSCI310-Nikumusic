package com.example.trojanrideshare.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class MoneyParserTest {

    private static void assertRejected(String text) {
        assertThrows(text, IllegalArgumentException.class, () -> MoneyParser.parsePositiveCents(text));
    }

    @Test
    public void parsesValidAmounts() {
        assertEquals(6_000, MoneyParser.parsePositiveCents("60"));
        assertEquals(6_050, MoneyParser.parsePositiveCents("60.5"));
        assertEquals(6_050, MoneyParser.parsePositiveCents(" 60.50 "));
        assertEquals(1, MoneyParser.parsePositiveCents("0.01"));
        assertEquals(50, MoneyParser.parsePositiveCents(".50"));
        assertEquals(6_000, MoneyParser.parsePositiveCents("60."));
    }

    @Test
    public void ignoresExtraTrailingZeros() {
        assertEquals(6_050, MoneyParser.parsePositiveCents("60.500"));
    }

    @Test
    public void rejectsMissingAmount() {
        assertRejected(null);
        assertRejected("");
        assertRejected("   ");
    }

    @Test
    public void rejectsZero() {
        assertRejected("0");
        assertRejected("0.00");
    }

    @Test
    public void rejectsMoreThanTwoDecimalPlaces() {
        assertRejected("60.505");
        assertRejected("0.001");
    }

    @Test
    public void rejectsNonNumbers() {
        assertRejected(".");
        assertRejected("abc");
        assertRejected("-5");
        assertRejected("1e5");
        assertRejected("1,000");
        assertRejected("$5");
    }

    @Test
    public void rejectsHugeAmounts() {
        assertRejected("99999999999999999999");
    }
}
