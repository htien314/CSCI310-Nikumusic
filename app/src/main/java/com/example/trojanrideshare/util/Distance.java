package com.example.trojanrideshare.util;

import com.example.trojanrideshare.model.Location;

import java.util.Locale;

/**
 * Team distance rules (owner: Nikhil). Every radius in the app is in miles, so ride search
 * and history search (Riya) use the same unit and the same "within radius" check.
 */
public final class Distance {

    private Distance() {
    }

    public static final String UNIT = "miles";

    /** True if both places exist and are at most radiusMiles apart. The boundary counts as inside. */
    public static boolean withinRadius(Location a, Location b, double radiusMiles) {
        if (a == null || b == null || !a.isValid() || !b.isValid()) {
            return false;
        }
        return a.distanceMilesTo(b) <= radiusMiles;
    }

    /** "0.4 mi", "12.0 mi". */
    public static String format(double miles) {
        return String.format(Locale.US, "%.1f mi", miles);
    }
}
