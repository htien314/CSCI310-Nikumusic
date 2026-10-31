package com.example.trojanrideshare.util;

import java.time.ZoneId;

/** Values shared by the whole team. Change them here, not in feature code. */
public final class Constants {

    private Constants() {
    }

    // Firestore collection names
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_RIDES = "rides";
    public static final String COLLECTION_JOIN_REQUESTS = "joinRequests";
    public static final String COLLECTION_COST_SHARES = "costShares";

    // Ride rules (TA confirmed: max riders includes the organizer)
    public static final int MIN_RIDERS = 2;
    public static final int MAX_RIDERS = 6;
    public static final int CANCEL_LEAVE_CUTOFF_HOURS = 3;

    // Times are stored as UTC Timestamps and displayed in Los Angeles time
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Los_Angeles");

    // Navigation argument keys
    public static final String ARG_RIDE_ID = "rideId";
}
