package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.model.SearchCriteria;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.Timestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds rides that fit a search (UR-3.1 to UR-3.7).
 *
 * Firestore can't filter by distance, so the repository loads the active rides and the
 * radius, date, time and seat checks run here. That also keeps the rules unit-testable.
 */
public class SearchService {

    public static final double MAX_RADIUS_MILES = 100;

    private final RideRepository rides;
    private final Clock clock;

    public SearchService(RideRepository rides, Clock clock) {
        this.rides = rides;
        this.clock = clock;
    }

    /** Reports the matching rides, sorted as the criteria asks, or an error with a message to show. */
    public void search(SearchCriteria criteria, Callback<List<SearchResult>> callback) {
        String problem = validate(criteria);
        if (problem != null) {
            callback.onError(new IllegalArgumentException(problem));
            return;
        }
        rides.findActive(new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> found) {
                callback.onSuccess(filterAndSort(found, criteria));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(new Exception("Couldn't load rides. Check your connection and try again.", e));
            }
        });
    }

    /** Returns a message describing what's wrong with the criteria, or null if they're fine. */
    static String validate(SearchCriteria criteria) {
        if (criteria == null || !isUsable(criteria.getPickup()) || !isUsable(criteria.getDestination())) {
            return "Enter a pickup location and a destination.";
        }
        double radius = criteria.getRadiusMiles();
        if (!(radius > 0)) {
            return "The radius has to be more than 0 miles.";
        }
        if (radius > MAX_RADIUS_MILES) {
            return "The radius can be at most " + (int) MAX_RADIUS_MILES + " miles.";
        }
        if (criteria.getSortOrder() == null) {
            return "Choose how to sort the results.";
        }
        return null;
    }

    List<SearchResult> filterAndSort(List<Ride> candidates, SearchCriteria criteria) {
        long now = clock.nowMillis();
        List<SearchResult> matches = new ArrayList<>();
        for (Ride ride : candidates) {
            SearchResult match = match(ride, criteria, now);
            if (match != null) {
                matches.add(match);
            }
        }
        sort(matches, criteria.getSortOrder());
        return matches;
    }

    /** The ride as a result if it fits every part of the search, otherwise null. */
    private static SearchResult match(Ride ride, SearchCriteria criteria, long now) {
        // Only rides people can still join: On-Time or Delayed (UR-3.5), not yet departed
        ActivityStatus status = ride.getActivityStatus();
        if (status != ActivityStatus.ON_TIME && status != ActivityStatus.DELAYED) {
            return null;
        }
        Timestamp departure = ride.getCurrentDepartureTime();
        if (departure == null || departure.toDate().getTime() < now) {
            return null;
        }
        if (!isUsable(ride.getOrigin()) || !isUsable(ride.getDestination())) {
            return null;
        }
        if (criteria.isOpenSeatsOnly() && ride.getAvailableSeats() == 0) {
            return null;
        }

        ZonedDateTime local = Instant.ofEpochMilli(departure.toDate().getTime()).atZone(Constants.DISPLAY_ZONE);
        if (criteria.getDate() != null && !local.toLocalDate().equals(criteria.getDate())) {
            return null;
        }
        if (!inTimeWindow(local.toLocalTime(), criteria.getEarliestTime(), criteria.getLatestTime())) {
            return null;
        }

        // Both ends have to be within the radius (UR-3.3)
        double originDistance = criteria.getPickup().distanceMilesTo(ride.getOrigin());
        if (originDistance > criteria.getRadiusMiles()) {
            return null;
        }
        double destinationDistance = criteria.getDestination().distanceMilesTo(ride.getDestination());
        if (destinationDistance > criteria.getRadiusMiles()) {
            return null;
        }
        return new SearchResult(ride, originDistance, destinationDistance);
    }

    /**
     * True if the time is inside the window. Either end may be null. A window like 10 PM to 2 AM,
     * where the start is after the end, runs past midnight.
     */
    static boolean inTimeWindow(LocalTime time, LocalTime earliest, LocalTime latest) {
        if (earliest == null && latest == null) {
            return true;
        }
        if (earliest == null) {
            return !time.isAfter(latest);
        }
        if (latest == null) {
            return !time.isBefore(earliest);
        }
        if (!earliest.isAfter(latest)) {
            return !time.isBefore(earliest) && !time.isAfter(latest);
        }
        return !time.isBefore(earliest) || !time.isAfter(latest);
    }

    /** Sorts in place (UR-3.6). The sort is stable, so ties keep their current order. */
    public static void sort(List<SearchResult> results, RideSortOrder order) {
        Comparator<SearchResult> byTime = Comparator.comparingLong(SearchResult::getDepartureMillis);
        Comparator<SearchResult> byProximity = Comparator.comparingDouble(SearchResult::getProximityMiles);
        switch (order) {
            case EARLIEST_FIRST:
                results.sort(byTime);
                break;
            case LATEST_FIRST:
                results.sort(byTime.reversed());
                break;
            case PROXIMITY:
                results.sort(byProximity);
                break;
            case PROXIMITY_THEN_TIME:
            default:
                results.sort(byProximity.thenComparing(byTime));
                break;
        }
    }

    private static boolean isUsable(Location location) {
        return location != null && location.isValid();
    }
}
