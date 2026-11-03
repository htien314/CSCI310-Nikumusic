package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Participant;
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
 * Finds rides that fit a search (UR-3.1 to UR-3.7) and lists upcoming rides to browse.
 *
 * Firestore can't filter by distance, so the repository loads the active rides and the
 * radius, date, time and seat checks run here. That also keeps the rules unit-testable.
 */
public class SearchService {

    public static final double MAX_RADIUS_MILES = 100;
    public static final int MAX_SEATS_FILTER = Constants.MAX_RIDERS - 1;

    static final String MESSAGE_PERMISSION = "You don't have permission to see rides. Make sure you're logged in.";
    static final String MESSAGE_NETWORK = "Couldn't load rides. Check your connection and try again.";

    private static final RideRepository.Subscription NO_SUBSCRIPTION = () -> {
    };

    private final RideRepository rides;
    private final Clock clock;

    public SearchService(RideRepository rides, Clock clock) {
        this.rides = rides;
        this.clock = clock;
    }

    /** Reports the matching rides once, sorted as the criteria asks, or an error with a message to show. */
    public void search(SearchCriteria criteria, Callback<List<SearchResult>> callback) {
        String problem = validate(criteria);
        if (problem != null) {
            callback.onError(new IllegalArgumentException(problem));
            return;
        }
        rides.findActive(resultsFrom(rideList -> filterAndSort(rideList, criteria), callback));
    }

    /**
     * Like {@link #search}, but reports the results again whenever rides change (someone joins,
     * a ride fills up, is delayed or cancelled), so the list never goes stale. Cancel when done.
     */
    public RideRepository.Subscription watch(SearchCriteria criteria, Callback<List<SearchResult>> callback) {
        String problem = validate(criteria);
        if (problem != null) {
            callback.onError(new IllegalArgumentException(problem));
            return NO_SUBSCRIPTION;
        }
        return rides.listenToActive(resultsFrom(rideList -> filterAndSort(rideList, criteria), callback));
    }

    /**
     * Upcoming rides that other users posted and that still have seats, earliest first, for browsing
     * before a search. Updates live like {@link #watch}.
     */
    public RideRepository.Subscription watchUpcoming(String excludeUserId, Callback<List<SearchResult>> callback) {
        return rides.listenToActive(resultsFrom(rideList -> upcoming(rideList, excludeUserId), callback));
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
        if (criteria.getMinSeats() < 0 || criteria.getMinSeats() > MAX_SEATS_FILTER) {
            return "Open seats has to be between 0 and " + MAX_SEATS_FILTER + ".";
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

    List<SearchResult> upcoming(List<Ride> candidates, String excludeUserId) {
        long now = clock.nowMillis();
        List<SearchResult> list = new ArrayList<>();
        for (Ride ride : candidates) {
            if (isJoinable(ride, now, excludeUserId) && ride.getAvailableSeats() > 0) {
                list.add(new SearchResult(ride, 0, 0));
            }
        }
        sort(list, RideSortOrder.EARLIEST_FIRST);
        return list;
    }

    /** The ride as a result if it fits every part of the search, otherwise null. */
    private static SearchResult match(Ride ride, SearchCriteria criteria, long now) {
        if (!isJoinable(ride, now, criteria.getExcludeUserId())) {
            return null;
        }
        if (ride.getAvailableSeats() < criteria.getMinSeats()) {
            return null;
        }

        ZonedDateTime local = Instant.ofEpochMilli(departureMillis(ride)).atZone(Constants.DISPLAY_ZONE);
        if (criteria.getDate() != null && !local.toLocalDate().equals(criteria.getDate())) {
            return null;
        }
        if (!inTimeWindow(local.toLocalTime(), criteria.getEarliestTime(), criteria.getLatestTime())) {
            return null;
        }

        // Both ends have to be within the radius (UR-3.3). The boundary counts as inside.
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
     * On-Time or Delayed (UR-3.5), not departed yet, with readable locations, and not a ride the
     * user already organizes or belongs to.
     */
    private static boolean isJoinable(Ride ride, long now, String excludeUserId) {
        ActivityStatus status = ride.getActivityStatus();
        if (status != ActivityStatus.ON_TIME && status != ActivityStatus.DELAYED) {
            return false;
        }
        Timestamp departure = ride.getCurrentDepartureTime();
        if (departure == null || departure.toDate().getTime() < now) {
            return false;
        }
        if (!isUsable(ride.getOrigin()) || !isUsable(ride.getDestination())) {
            return false;
        }
        return excludeUserId == null || !involves(ride, excludeUserId);
    }

    private static boolean involves(Ride ride, String userId) {
        if (ride.isOrganizer(userId)) {
            return true;
        }
        if (ride.getParticipants() != null) {
            for (Participant participant : ride.getParticipants()) {
                if (userId.equals(participant.getUserId())) {
                    return true;
                }
            }
        }
        return false;
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

    /** A user-facing message for a repository error. */
    static String friendlyMessage(Exception e) {
        if (e instanceof SecurityException) {
            return MESSAGE_PERMISSION;
        }
        return MESSAGE_NETWORK;
    }

    private static long departureMillis(Ride ride) {
        return ride.getCurrentDepartureTime().toDate().getTime();
    }

    private static boolean isUsable(Location location) {
        return location != null && location.isValid();
    }

    private interface Transform {
        List<SearchResult> apply(List<Ride> rides);
    }

    private static Callback<List<Ride>> resultsFrom(Transform transform, Callback<List<SearchResult>> callback) {
        return new Callback<List<Ride>>() {
            @Override
            public void onSuccess(List<Ride> found) {
                callback.onSuccess(transform.apply(found));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(new Exception(friendlyMessage(e), e));
            }
        };
    }
}
