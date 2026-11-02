package com.example.trojanrideshare.model;

/** How search results are ordered (UR-3.6). Proximity is origin distance plus destination distance (UR-3.7). */
public enum RideSortOrder {
    PROXIMITY_THEN_TIME,
    PROXIMITY,
    EARLIEST_FIRST,
    LATEST_FIRST
}
