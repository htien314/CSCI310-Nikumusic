package com.example.trojanrideshare.model;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the user searched for (UR-3.1 to UR-3.4). Not stored in Firestore.
 * Date and times are in Los Angeles time; null means "any". Build with {@link Builder}.
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
    private final int minSeats;
    @Nullable
    private final String excludeUserId;
    private final RideSortOrder sortOrder;

    private SearchCriteria(Builder b) {
        pickup = b.pickup;
        destination = b.destination;
        radiusMiles = b.radiusMiles;
        date = b.date;
        earliestTime = b.earliestTime;
        latestTime = b.latestTime;
        minSeats = b.minSeats;
        excludeUserId = b.excludeUserId;
        sortOrder = b.sortOrder;
    }

    public Location getPickup() {
        return pickup;
    }

    public Location getDestination() {
        return destination;
    }

    /** The most a ride's origin and destination may each be from the user's locations, in miles (UR-3.2). */
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

    /** Rides need at least this many open seats. 0 also shows Full rides. */
    public int getMinSeats() {
        return minSeats;
    }

    /** Rides this user organizes or has joined are left out of results. Null leaves none out. */
    @Nullable
    public String getExcludeUserId() {
        return excludeUserId;
    }

    public RideSortOrder getSortOrder() {
        return sortOrder;
    }

    public static final class Builder {
        private final Location pickup;
        private final Location destination;
        private final double radiusMiles;
        private LocalDate date;
        private LocalTime earliestTime;
        private LocalTime latestTime;
        private int minSeats;
        private String excludeUserId;
        private RideSortOrder sortOrder = RideSortOrder.PROXIMITY_THEN_TIME;

        public Builder(Location pickup, Location destination, double radiusMiles) {
            this.pickup = pickup;
            this.destination = destination;
            this.radiusMiles = radiusMiles;
        }

        public Builder date(@Nullable LocalDate value) {
            date = value;
            return this;
        }

        /** Either end may be null. If earliest is after latest, the window runs past midnight. */
        public Builder timeWindow(@Nullable LocalTime earliest, @Nullable LocalTime latest) {
            earliestTime = earliest;
            latestTime = latest;
            return this;
        }

        public Builder minSeats(int value) {
            minSeats = value;
            return this;
        }

        public Builder excludeRidesOf(@Nullable String userId) {
            excludeUserId = userId;
            return this;
        }

        public Builder sortOrder(RideSortOrder value) {
            sortOrder = value;
            return this;
        }

        public SearchCriteria build() {
            return new SearchCriteria(this);
        }
    }
}
