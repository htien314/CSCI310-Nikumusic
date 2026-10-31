package com.example.trojanrideshare.util;

/**
 * Single source of "now" for business rules (3-hour cutoff, departure checks,
 * 7-day payment flags). Always get the current time from a Clock instead of
 * calling System.currentTimeMillis() directly.
 */
public interface Clock {
    long nowMillis();
}
