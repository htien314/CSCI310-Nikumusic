package com.example.trojanrideshare.util;

import java.util.Locale;

/** Formats amounts stored as integer cents for display. */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    public static String format(long cents) {
        return String.format(Locale.US, "$%d.%02d", cents / 100, cents % 100);
    }
}
