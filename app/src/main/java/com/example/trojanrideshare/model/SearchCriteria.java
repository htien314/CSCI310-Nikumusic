package com.example.trojanrideshare.model;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the user searched for (UR-3.1 to UR-3.4). Not stored in Firestore.
 * Date and times are in Los Angeles time; null means "any".
 */
public final class SearchCriteria {

    private final Location pickup;
    private final Location destination;
    private final double radiusMiles;
    @Nullable
    private final LocalDate date;
    @Nullable
    private final LocalTime earliestTime;
    @Nullable
    private final LocalTime latestTime;
    private final boolean openSeatsOnly;
    private final RideSortOrder sortOrder;

    public SearchCriteria(Location pickup, Location destination, double radiusMiles,
                          @Nullable LocalDate date, @Nullable LocalTime earliestTime,
                          @Nullable LocalTime latestTime, boolean openSeatsOnly, RideSortOrder sortOrder) {
        this.pickup = pickup;
        this.destination = destination;
        this.radiusMiles = radiusMiles;
        this.date = date;
        this.earliestTime = earliestTime;
        this.latestTime = latestTime;
        this.openSeatsOnly = openSeatsOnly;
        this.sortOrder = sortOrder;
    }

    public Location getPickup() {
        return pickup;
    }

    public Location getDestination() {
        return destination;
    }

    /** The most a ride's origin and destination may each be from the user's locations (UR-3.2). */
    public double getRadiusMiles() {
        return radiusMiles;
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    @Nullable
    public LocalTime getEarliestTime() {
        return earliestTime;
    }

    @Nullable
    public LocalTime getLatestTime() {
        return latestTime;
    }

    public boolean isOpenSeatsOnly() {
        return openSeatsOnly;
    }

    public RideSortOrder getSortOrder() {
        return sortOrder;
    }
}
