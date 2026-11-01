package com.example.trojanrideshare.util;

import java.math.BigDecimal;

/** Turns a typed USD amount into integer cents. Shared by expected and actual ride costs. */
public final class MoneyParser {

    private MoneyParser() {
    }

    /**
     * Parses text like "60", "60.5" or "60.50" into cents.
     *
     * @throws IllegalArgumentException with a message to show the user if the amount is
     *                                  missing, not a number, $0 or less, or has more than
     *                                  two decimal places
     */
    public static long parsePositiveCents(String text) {
        String amount = text == null ? "" : text.trim();
        if (amount.isEmpty()) {
            throw new IllegalArgumentException("Enter a cost.");
        }
        if (!amount.matches("\\d*\\.?\\d*") || amount.equals(".")) {
            throw new IllegalArgumentException("Enter the cost as a number, like 60.50.");
        }
        BigDecimal dollars = new BigDecimal(amount);
        if (dollars.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Cost can't have more than two decimal places.");
        }
        if (dollars.signum() <= 0) {
            throw new IllegalArgumentException("Cost must be more than $0.");
        }
        try {
            return dollars.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Cost is too large.");
        }
    }
}
