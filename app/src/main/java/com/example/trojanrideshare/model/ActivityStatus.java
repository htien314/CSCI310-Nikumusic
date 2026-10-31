package com.example.trojanrideshare.model;

/** Where a ride stands. CANCELLED and COMPLETED are final (UR-2.10). */
public enum ActivityStatus {
    ON_TIME,
    DELAYED,
    CANCELLED,
    COMPLETED
}
