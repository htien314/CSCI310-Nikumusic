package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.data.RideRepository;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private int updates;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        service = new SearchService(repository, () -> NOW.toInstant().toEpochMilli());
        results = null;
        error = null;
        updates = 0;
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

    private static void addRider(Ride ride, String userId) {
        ride.getParticipants().add(new Participant(userId, "Rider", at(NOW)));
        ride.syncCapacityStatus();
    }

    private static void fill(Ride ride) {
        while (ride.getAvailableSeats() > 0) {
            addRider(ride, "rider" + ride.getParticipants().size());
        }
    }

    private static SearchCriteria.Builder criteria(Location pickup, Location destination, double radius) {
        return new SearchCriteria.Builder(pickup, destination, radius);
    }

    private Callback<List<SearchResult>> collector() {
        return new Callback<List<SearchResult>>() {
            @Override
            public void onSuccess(List<SearchResult> result) {
                results = result;
                updates++;
            }

            @Override
            public void onError(Exception e) {
                error = e;
            }
        };
    }

    private void search(SearchCriteria.Builder criteria) {
        service.search(criteria.build(), collector());
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
        assertEquals(Arrays.asList(expected), ids());
    }

    // ---- radius: both ends must match (UR-3.3) ----

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
    public void excludesRideWhenOnlyDestinationMatches() {
        addRide("farOrigin", UNION_STATION, LAX, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds();
    }

    @Test
    public void excludesRideWhenOnlyOriginMatches() {
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

    @Test
    public void rideExactlyOnTheRadiusBoundaryMatches() {
        addRide("edge", FIGUEROA, LAX, NOW.plusDays(1));
        double exact = USC_VILLAGE.distanceMilesTo(FIGUEROA);

        search(criteria(USC_VILLAGE, LAX, exact));
        assertIds("edge");

        search(criteria(USC_VILLAGE, LAX, exact - 0.001));
        assertIds();
    }

    @Test
    public void destinationBoundaryIsCheckedSeparately() {
        addRide("edge", USC_VILLAGE, LAX_HOTELS, NOW.plusDays(1));
        double exact = LAX.distanceMilesTo(LAX_HOTELS);

        search(criteria(USC_VILLAGE, LAX, exact));
        assertIds("edge");

        search(criteria(USC_VILLAGE, LAX, exact - 0.001));
        assertIds();
    }

    @Test
    public void skipsRidesWithMalformedLocations() {
        Ride noOrigin = addRide("noOrigin", USC_VILLAGE, LAX, NOW.plusDays(1));
        noOrigin.setOrigin(null);
        Ride badCoordinates = addRide("badCoordinates", USC_VILLAGE, LAX, NOW.plusDays(1));
        badCoordinates.setDestination(new Location("Nowhere", 200, 500));
        addRide("good", USC_VILLAGE, LAX, NOW.plusDays(1));

        search(criteria(USC_VILLAGE, LAX, 2));
        assertIds("good");
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
        assertTrue(service.filterAndSort(all, criteria(USC_VILLAGE, LAX, 2).build()).isEmpty());
        assertTrue(service.upcoming(all, null).isEmpty());
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

    // ---- other users' rides only ----

    @Test
    public void excludesRidesTheUserOrganizes() {
        addRide("mine", USC_VILLAGE, LAX, NOW.plusDays(1));
        addRide("theirs", USC_VILLAGE, LAX, NOW.plusDays(2));
        search(criteria(USC_VILLAGE, LAX, 2).excludeRidesOf("organizer-mine").sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("theirs");
    }

    @Test
    public void excludesRidesTheUserAlreadyJoined() {
        Ride joined = addRide("joined", USC_VILLAGE, LAX, NOW.plusDays(1));
        addRider(joined, "me");
        addRide("other", USC_VILLAGE, LAX, NOW.plusDays(2));
        search(criteria(USC_VILLAGE, LAX, 2).excludeRidesOf("me").sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("other");
    }

    @Test
    public void noUserMeansNothingIsExcluded() {
        addRide("ride", USC_VILLAGE, LAX, NOW.plusDays(1));
        search(criteria(USC_VILLAGE, LAX, 2).excludeRidesOf(null));
        assertIds("ride");
    }

    // ---- filters (UR-3.4) ----

    @Test
    public void fullRidesShowOnlyWhenSeatsFilterIsAny() {
        fill(addRide("full", USC_VILLAGE, LAX, NOW.plusDays(1)));
        addRide("open", USC_VILLAGE, LAX, NOW.plusDays(2));

        search(criteria(USC_VILLAGE, LAX, 2).minSeats(0).sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("full", "open");

        search(criteria(USC_VILLAGE, LAX, 2).minSeats(1).sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("open");
    }

    @Test
    public void minSeatsFilterBoundary() {
        Ride twoLeft = addRide("twoLeft", USC_VILLAGE, LAX, NOW.plusDays(1), 4);
        addRider(twoLeft, "rider");
        addRide("threeLeft", USC_VILLAGE, LAX, NOW.plusDays(2), 4);

        search(criteria(USC_VILLAGE, LAX, 2).minSeats(2).sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("twoLeft", "threeLeft");

        search(criteria(USC_VILLAGE, LAX, 2).minSeats(3).sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("threeLeft");
    }

    @Test
    public void dateFilterUsesLosAngelesDate() {
        // 11:30 PM Nov 2 in LA is already Nov 3 in UTC
        addRide("lateNov2", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 23, 30, 0, 0, LA));
        addRide("nov3", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, LA));

        search(criteria(USC_VILLAGE, LAX, 2).date(LocalDate.of(2026, 11, 2)));
        assertIds("lateNov2");
    }

    @Test
    public void timeWindowIsInclusive() {
        addRide("early", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 7, 59, 0, 0, LA));
        addRide("start", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, LA));
        addRide("end", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, LA));
        addRide("late", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 10, 1, 0, 0, LA));

        search(criteria(USC_VILLAGE, LAX, 2).timeWindow(LocalTime.of(8, 0), LocalTime.of(10, 0))
                .sortOrder(RideSortOrder.EARLIEST_FIRST));
        assertIds("start", "end");
    }

    @Test
    public void timeWindowWithOnlyOneEnd() {
        addRide("morning", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, LA));
        addRide("evening", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, LA));

        search(criteria(USC_VILLAGE, LAX, 2).timeWindow(LocalTime.of(12, 0), null));
        assertIds("evening");

        search(criteria(USC_VILLAGE, LAX, 2).timeWindow(null, LocalTime.of(12, 0)));
        assertIds("morning");
    }

    @Test
    public void timeWindowUsesDelayedTime() {
        Ride ride = addRide("delayed", USC_VILLAGE, LAX, ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, LA));
        ride.setActivityStatus(ActivityStatus.DELAYED);
        ride.setDelayedDepartureTime(at(ZonedDateTime.of(2026, 11, 2, 11, 0, 0, 0, LA)));

        search(criteria(USC_VILLAGE, LAX, 2).timeWindow(LocalTime.of(10, 30), LocalTime.of(11, 30)));
        assertIds("delayed");
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
        search(criteria(USC_VILLAGE, LAX, 2).sortOrder(order));
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

    // ---- browsing ----

    @Test
    public void upcomingListsOtherUsersOpenRidesEarliestFirst() {
        addRide("later", USC_VILLAGE, LAX, NOW.plusDays(2));
        addRide("sooner", UNION_STATION, SANTA_MONICA, NOW.plusDays(1));
        addRide("mine", USC_VILLAGE, LAX, NOW.plusHours(1));
        fill(addRide("full", USC_VILLAGE, LAX, NOW.plusHours(2)));
        addRide("past", USC_VILLAGE, LAX, NOW.minusHours(1));

        service.watchUpcoming("organizer-mine", collector());
        assertIds("sooner", "later");
    }

    // ---- live updates ----

    @Test
    public void watchReportsAgainWhenRidesChange() {
        Ride ride = addRide("ride", USC_VILLAGE, LAX, NOW.plusDays(1));
        RideRepository.Subscription subscription = service.watch(criteria(USC_VILLAGE, LAX, 2).build(), collector());
        assertIds("ride");

        // Another user cancels the ride
        ride.setActivityStatus(ActivityStatus.CANCELLED);
        repository.notifyListeners();
        assertIds();
        assertEquals(2, updates);

        subscription.cancel();
        addRide("new", USC_VILLAGE, LAX, NOW.plusDays(1));
        repository.notifyListeners();
        assertEquals("No updates after cancel", 2, updates);
        assertTrue(repository.listeners.isEmpty());
    }

    @Test
    public void watchDropsRideWhenItFillsUpAndSeatsAreRequired() {
        Ride ride = addRide("ride", USC_VILLAGE, LAX, NOW.plusDays(1));
        service.watch(criteria(USC_VILLAGE, LAX, 2).minSeats(1).build(), collector());
        assertIds("ride");

        fill(ride);
        repository.notifyListeners();
        assertIds();
    }

    @Test
    public void watchWithBadCriteriaReportsErrorAndDoesNotListen() {
        RideRepository.Subscription subscription = service.watch(criteria(USC_VILLAGE, LAX, 0).build(), collector());
        assertNotNull(error);
        assertTrue(repository.listeners.isEmpty());
        subscription.cancel();
    }

    // ---- validation and errors ----

    private void assertRejected(SearchCriteria.Builder criteria, String message) {
        results = null;
        error = null;
        search(criteria);
        assertNull(results);
        assertNotNull(error);
        assertEquals(message, error.getMessage());
    }

    @Test
    public void rejectsMissingLocations() {
        assertRejected(criteria(null, LAX, 2), "Enter a pickup location and a destination.");
        assertRejected(criteria(USC_VILLAGE, null, 2), "Enter a pickup location and a destination.");
        assertRejected(criteria(new Location("", 34, -118), LAX, 2), "Enter a pickup location and a destination.");
    }

    @Test
    public void rejectsBadRadius() {
        assertRejected(criteria(USC_VILLAGE, LAX, 0), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, -1), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, Double.NaN), "The radius has to be more than 0 miles.");
        assertRejected(criteria(USC_VILLAGE, LAX, 100.5), "The radius can be at most 100 miles.");
    }

    @Test
    public void acceptsMaximumRadius() {
        search(criteria(USC_VILLAGE, LAX, 100));
        assertNull(error);
    }

    @Test
    public void rejectsBadSeatCount() {
        assertRejected(criteria(USC_VILLAGE, LAX, 2).minSeats(-1), "Open seats has to be between 0 and 5.");
        assertRejected(criteria(USC_VILLAGE, LAX, 2).minSeats(6), "Open seats has to be between 0 and 5.");
    }

    @Test
    public void networkFailureGivesFriendlyMessage() {
        repository.findActiveError = new IOException("UNAVAILABLE");
        search(criteria(USC_VILLAGE, LAX, 2));
        assertNull(results);
        assertEquals("Couldn't load rides. Check your connection and try again.", error.getMessage());
    }

    @Test
    public void permissionFailureSaysToLogIn() {
        repository.findActiveError = new SecurityException("PERMISSION_DENIED");
        search(criteria(USC_VILLAGE, LAX, 2));
        assertNull(results);
        assertEquals("You don't have permission to see rides. Make sure you're logged in.", error.getMessage());
    }

    @Test
    public void noRidesGivesEmptyList() {
        search(criteria(USC_VILLAGE, LAX, 2));
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
