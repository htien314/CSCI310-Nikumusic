package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.model.SearchCriteria;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class SearchServiceTest {

    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, LA);

    private static final Location USC_VILLAGE = new Location("USC Village", 34.0256, -118.2850);
    // About 0.3 miles from USC Village
    private static final Location FIGUEROA = new Location("Figueroa & Jefferson", 34.0220, -118.2810);
    private static final Location UNION_STATION = new Location("Union Station", 34.0562, -118.2365);
    private static final Location LAX = new Location("LAX", 33.9416, -118.4085);
    // About 1.2 miles from LAX
    private static final Location LAX_HOTELS = new Location("Century Blvd hotels", 33.9455, -118.3870);
    private static final Location SANTA_MONICA = new Location("Santa Monica Pier", 34.0094, -118.4973);

    private FakeRideRepository repository;
    private SearchService service;
    private List<SearchResult> results;
    private Exception error;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        service = new SearchService(repository, () -> NOW.toInstant().toEpochMilli());
        results = null;
        error = null;
    }

    // ---- helpers ----

    private static Timestamp at(ZonedDateTime time) {
        return new Timestamp(Date.from(time.toInstant()));
    }

    /** Adds a ride with the organizer as its only rider. */
    private Ride addRide(String id, Location origin, Location destination, ZonedDateTime departure, int maxRiders) {
        Ride ride = Ride.createNew(id, origin, destination, at(departure), maxRiders, 4000L, null,
                "organizer-" + id, "Organizer", at(NOW.minusDays(1)));
        ride.setId(id);
        repository.rides.put(id, ride);
        return ride;
    }

    private Ride addRide(String id, Location origin, Location destination, ZonedDateTime departure) {
        return addRide(id, origin, destination, departure, 4);
    }

    private void fill(Ride ride) {
        while (ride.getAvailableSeats() > 0) {
            ride.getParticipants().add(new Participant("rider" + ride.getParticipants().size(), "Rider",
                    at(NOW)));
        }
        ride.syncCapacityStatus();
    }

    private SearchCriteria criteria(Location pickup, Location destination, double radius) {
        return new SearchCriteria(pickup, destination, radius, null, null, null, false,
                RideSortOrder.PROXIMITY_THEN_TIME);
    }

    private void search(SearchCriteria criteria) {
        service.search(criteria, new Callback<List<SearchResult>>() {
            @Override
            public void onSuccess(List<SearchResult> result) {
                results = result;
            }

            @Override
            public void onError(Exception e) {
                error = e;
            }
        });
    }

    private List<String> ids() {
        assertNull("Unexpected error: " + error, error);
        List<String> ids = new ArrayList<>();
        for (SearchResult result : results) {
            ids.add(result.getRide().getId());
        }
        return ids;
    }

    private void assertIds(String... expected) {
        assertEquals(java.util.Arrays.asList(expected), ids());
    }

    // ---- radius (UR-3.3) ----

    @Test
    public void matchesWhenBothEndsAreWithinRadius() {
        addRide("near", FIGUEROA, LAX_HOTELS, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("near");
        SearchResult result = results.get(0);
        assertEquals(0.33, result.getOriginDistanceMiles(), 0.1);
        assertEquals(1.25, result.getDestinationDistanceMiles(), 0.15);
        assertEquals(result.getOriginDistanceMiles() + result.getDestinationDistanceMiles(),
                result.getProximityMiles(), 1e-9);
    }

    @Test
    public void excludesRideWhenOriginIsOutsideRadius() {
        addRide("farOrigin", UNION_STATION, LAX, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds();
    }

    @Test
    public void excludesRideWhenDestinationIsOutsideRadius() {
        addRide("farDestination", USC_VILLAGE, SANTA_MONICA, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds();
    }

    @Test
    public void largerRadiusIncludesMoreRides() {
        addRide("farOrigin", UNION_STATION, LAX, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 10));
        assertIds("farOrigin");
    }

    @Test
    public void doesNotMatchReversedTrip() {
        addRide("reversed", LAX, USC_VILLAGE, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds();
    }

    // ---- status and time ----

    @Test
    public void delayedRideWithOpenSeatsStillShowsUp() {
        Ride ride = addRide("delayed", USC_VILLAGE, LAX, NOW.plusDays(1));
        ride.setActivityStatus(ActivityStatus.DELAYED);
        ride.setDelayedDepartureTime(at(NOW.plusDays(1).plusHours(1)));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("delayed");
    }

    @Test
    public void excludesCancelledAndCompletedRides() {
        Ride cancelled = addRide("cancelled", USC_VILLAGE, LAX, NOW.plusDays(1));
        cancelled.setActivityStatus(ActivityStatus.CANCELLED);
        Ride completed = addRide("completed", USC_VILLAGE, LAX, NOW.plusDays(1));
        completed.setActivityStatus(ActivityStatus.COMPLETED);
        // Search re-checks status even if the repository returns them
        List<Ride> all = new ArrayList<>(repository.rides.values());
        assertTrue(service.filterAndSort(all, criteria(USC_VILLAGE, LAX, 2)).isEmpty());
    }

    @Test
    public void excludesRidesThatAlreadyLeft() {
        addRide("past", USC_VILLAGE, LAX, NOW.minusMinutes(1));
        addRide("future", USC_VILLAGE, LAX, NOW.plusMinutes(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("future");
    }

    @Test
    public void delayedRideUsesItsNewTimeForThePastCheck() {
        Ride ride = addRide("delayed", USC_VILLAGE, LAX, NOW.minusMinutes(30));
        ride.setActivityStatus(ActivityStatus.DELAYED);
        ride.setDelayedDepartureTime(at(NOW.plusMinutes(30)));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("delayed");
    }

    // ---- filters (UR-3.4) ----

    @Test
    public void fullRidesShowUnlessOpenSeatsOnly() {
        fill(addRide("full", USC_VILLAGE, LAX, NOW.plusDays(1)));
        addRide("open", USC_VILLAGE, LAX, NOW.plusDays(2));

        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("full", "open");

        search(new SearchCriteria(USC_VILLAGE, LAX, 2, null, null, null, true, RideSortOrder.EARLIEST_FIRST));
        assertIds("open");
    }

    @Test
    public void dateFilterUsesLosAngelesDate() {
        // 11:30 PM Nov 2 in LA is already Nov 3 in UTC
        addRide("lateNov2", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 23, 30, 0, 0, LA));
        addRide("nov3", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, LA));

        search(new SearchCriteria(USC_VILLAGE, LAX, 2, LocalDate.of(2026, 11, 2), null, null, false,
                RideSortOrder.EARLIEST_FIRST));
        assertIds("lateNov2");
    }

    @Test
    public void timeWindowIsInclusive() {
        addRide("early", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 7, 59, 0, 0, LA));
        addRide("start", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, LA));
        addRide("end", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, LA));
        addRide("late", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 10, 1, 0, 0, LA));

        search(new SearchCriteria(USC_VILLAGE, LAX, 2, null, LocalTime.of(8, 0), LocalTime.of(10, 0), false,
                RideSortOrder.EARLIEST_FIRST));
        assertIds("start", "end");
    }

    @Test
    public void timeWindowWithOnlyOneEnd() {
        addRide("morning", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, LA));
        addRide("evening", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, LA));

        search(new SearchCriteria(USC_VILLAGE, LAX, 2, null, LocalTime.of(12, 0), null, false,
                RideSortOrder.EARLIEST_FIRST));
        assertIds("evening");

        search(new SearchCriteria(USC_VILLAGE, LAX, 2, null, null, LocalTime.of(12, 0), false,
                RideSortOrder.EARLIEST_FIRST));
        assertIds("morning");
    }

    @Test
    public void inTimeWindow_overnightWindow() {
        LocalTime from = LocalTime.of(22, 0);
        LocalTime to = LocalTime.of(2, 0);
        assertTrue(SearchService.inTimeWindow(LocalTime.of(23, 30), from, to));
        assertTrue(SearchService.inTimeWindow(LocalTime.of(1, 0), from, to));
        assertFalse(SearchService.inTimeWindow(LocalTime.of(12, 0), from, to));
    }

    // ---- sorting (UR-3.6, UR-3.7) ----

    private void addSortingRides() {
        // "close" is nearest but leaves last; "farEarly" and "farLate" tie on proximity
        addRide("farLate", FIGUEROA, LAX_HOTELS, NOW.plusHours(5));
        addRide("close", USC_VILLAGE, LAX, NOW.plusHours(6));
        addRide("farEarly", FIGUEROA, LAX_HOTELS, NOW.plusHours(2));
        addRide("middle", USC_VILLAGE, LAX_HOTELS, NOW.plusHours(4));
    }

    private void searchSorted(RideSortOrder order) {
        search(new SearchCriteria(USC_VILLAGE, LAX, 2, null, null, null, false, order));
    }

    @Test
    public void sortEarliestFirst() {
        addSortingRides();
        searchSorted(RideSortOrder.EARLIEST_FIRST);
        assertIds("farEarly", "middle", "farLate", "close");
    }

    @Test
    public void sortLatestFirst() {
        addSortingRides();
        searchSorted(RideSortOrder.LATEST_FIRST);
        assertIds("close", "farLate", "middle", "farEarly");
    }

    @Test
    public void sortByProximity() {
        addSortingRides();
        searchSorted(RideSortOrder.PROXIMITY);
        List<String> ids = ids();
        assertEquals("close", ids.get(0));
        assertEquals("middle", ids.get(1));
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getProximityMiles() <= results.get(i).getProximityMiles());
        }
    }

    @Test
    public void sortByProximityThenTimeBreaksTiesByEarliest() {
        addSortingRides();
        searchSorted(RideSortOrder.PROXIMITY_THEN_TIME);
        assertIds("close", "middle", "farEarly", "farLate");
    }

    @Test
    public void resortingExistingResults() {
        addSortingRides();
        searchSorted(RideSortOrder.PROXIMITY_THEN_TIME);
        SearchService.sort(results, RideSortOrder.EARLIEST_FIRST);
        assertIds("farEarly", "middle", "farLate", "close");
    }

    // ---- validation and errors ----

    private void assertRejected(SearchCriteria criteria, String message) {
        search(criteria);
        assertNull(results);
        assertNotNull(error);
        assertEquals(message, error.getMessage());
    }

    @Test
    public void rejectsMissingLocations() {
        assertRejected(criteria(null, LAX, 2), "Enter a pickup location and a destination.");
        assertRejected(criteria(USC_VILLAGE, null, 2), "Enter a pickup location and a destination.");
    }

    @Test
    public void rejectsBadRadius() {
        assertRejected(criteria(USC_VILLAGE, LAX, 0), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, -1), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, Double.NaN), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, 100.5), "The radius can be at most 100 miles.");
    }

    @Test
    public void reportsFriendlyMessageWhenLoadingFails() {
        repository.findActiveError = new RuntimeException("UNAVAILABLE");
        search(criteria(USC_VILLAGE, LAX, 2));
        assertNull(results);
        assertEquals("Couldn't load rides. Check your connection and try again.", error.getMessage());
    }

    @Test
    public void noRidesGivesEmptyList() {
        search(criteria(USC_VILLAGE, LAX, 2));
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
