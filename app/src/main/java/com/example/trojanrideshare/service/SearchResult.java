package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.Ride;

/** A ride that matched a search, with how far its origin and destination are from what the user entered. */
public final class SearchResult {

    private final Ride ride;
    private final double originDistanceMiles;
    private final double destinationDistanceMiles;

    public SearchResult(Ride ride, double originDistanceMiles, double destinationDistanceMiles) {
        this.ride = ride;
        this.originDistanceMiles = originDistanceMiles;
        this.destinationDistanceMiles = destinationDistanceMiles;
    }

    public Ride getRide() {
        return ride;
    }

    /** Distance from the user's pickup location to the ride's origin. */
    public double getOriginDistanceMiles() {
        return originDistanceMiles;
    }

    /** Distance from the user's destination to the ride's destination. */
    public double getDestinationDistanceMiles() {
        return destinationDistanceMiles;
    }

    /** Origin distance plus destination distance (UR-3.7). */
    public double getProximityMiles() {
        return originDistanceMiles + destinationDistanceMiles;
    }

    /** The ride's current departure time (the delayed time if it has one). */
    public long getDepartureMillis() {
        return ride.getCurrentDepartureTime().toDate().getTime();
    }
}
