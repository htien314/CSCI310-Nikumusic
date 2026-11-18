package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.FakeRideRepository;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Project 2.4 white-box tests WB11-WB15 (author: Nikhil, search and locations).
 *
 * Targets SearchService: search, validate, the private match step (reached through
 * filterAndSort), inTimeWindow and sort. Uses an in-memory repository and a fixed clock.
 * Lives in the service package so it can call the package-private methods directly.
 */
public class SearchLocationWhiteBoxTest {

    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    /** Fixed "now": Sunday, Nov 1 2026, 8:00 AM in Los Angeles. */
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, LA);
    private static final LocalDate TOMORROW = NOW.toLocalDate().plusDays(1);

    private static final Location USC = new Location("USC", 34.0224, -118.2851);
    private static final Location LAX = new Location("LAX", 33.9416, -118.4085);
    private static final String SEARCHER = "searcher-B";

    /** Counts findActive calls so tests can tell whether search reached the repository. */
    private static class CountingRideRepository extends FakeRideRepository {
        int findActiveCalls;

        @Override
        public void findActive(Callback<List<Ride>> callback) {
            findActiveCalls++;
            super.findActive(callback);
        }
    }

    private CountingRideRepository repository;
    private SearchService service;
    private List<SearchResult> results;
    private Exception error;
    private int successCalls;

    @Before
    public void setUp() {
        repository = new CountingRideRepository();
        service = new SearchService(repository, () -> NOW.toInstant().toEpochMilli());
        results = null;
        error = null;
        successCalls = 0;
    }

    // ---------------------------------------------------------------- helpers

    private static Timestamp at(ZonedDateTime time) {
        return new Timestamp(Date.from(time.toInstant()));
    }

    private static ZonedDateTime tomorrowAt(int hour, int minute) {
        return ZonedDateTime.of(TOMORROW, LocalTime.of(hour, minute), LA);
    }

    /** A point the given number of miles due north of a place (1 degree of latitude is about 69.05 miles). */
    private static Location northOf(Location place, double miles, String name) {
        return new Location(name, place.getLatitude() + miles / 69.05, place.getLongitude());
    }

    /** Ride organized by user A, who is its only rider, so open seats = maxRiders - 1. */
    private static Ride ride(String id, Location origin, Location destination, ZonedDateTime departure, int maxRiders) {
        Ride ride = Ride.createNew("Ride " + id, origin, destination, at(departure), maxRiders, 4000L, null,
                "organizer-A", "Organizer A", at(NOW.minusDays(1)));
        ride.setId(id);
        return ride;
    }

    private static void fill(Ride ride) {
        while (ride.getAvailableSeats() > 0) {
            ride.getParticipants().add(new Participant("rider" + ride.getParticipants().size(), "Rider", at(NOW)));
        }
        ride.syncParticipantIds();
        ride.syncCapacityStatus();
    }

    private void add(Ride ride) {
        repository.rides.put(ride.getId(), ride);
    }

    private static SearchCriteria.Builder criteria(double radius) {
        return new SearchCriteria.Builder(USC, LAX, radius).excludeRidesOf(SEARCHER);
    }

    private void search(SearchCriteria criteria) {
        service.search(criteria, new Callback<List<SearchResult>>() {
            @Override
            public void onSuccess(List<SearchResult> found) {
                results = found;
                successCalls++;
            }

            @Override
            public void onError(Exception e) {
                error = e;
            }
        });
    }

    private static List<String> ids(List<SearchResult> list) {
        List<String> ids = new ArrayList<>();
        for (SearchResult result : list) {
            ids.add(result.getRide().getId());
        }
        return ids;
    }

    // ------------------------------------------------------- WB11 find a match

    /**
     * WB11: valid criteria and one matching candidate. Covers validate() returning null, the
     * findActive success callback, every match() check passing, the candidate being added, and
     * the result reaching the caller.
     */
    @Test
    public void wb11_searchWithValidCriteriaReturnsMatchingRideId() {
        Ride match = ride("ride-11", northOf(USC, 0.5, "Near USC"), northOf(LAX, 0.5, "Near LAX"),
                tomorrowAt(10, 0), 4);
        add(match);
        SearchCriteria valid = criteria(5).build();

        assertNull("Valid criteria should pass validation", SearchService.validate(valid));
        search(valid);

        assertNull("No error expected: " + error, error);
        assertEquals(1, successCalls);
        assertEquals(1, repository.findActiveCalls);
        assertEquals(Collections.singletonList("ride-11"), ids(results));
        assertEquals(0.5, results.get(0).getOriginDistanceMiles(), 0.01);
        assertEquals(0.5, results.get(0).getDestinationDistanceMiles(), 0.01);
    }

    // --------------------------------------------- WB12 both endpoints in radius

    /**
     * WB12: coordinates just inside and just outside each endpoint radius. Covers the
     * origin-distance rejection, the destination-distance rejection, and the match branch.
     * The radius boundary itself counts as inside.
     */
    @Test
    public void wb12_bothEndpointsMustBeInsideRadius() {
        double radius = 5;
        Location justInsideUsc = northOf(USC, 4.99, "Just inside USC radius");
        Location justOutsideUsc = northOf(USC, 5.01, "Just outside USC radius");
        Location justInsideLax = northOf(LAX, 4.99, "Just inside LAX radius");
        Location justOutsideLax = northOf(LAX, 5.01, "Just outside LAX radius");
        assertTrue(USC.distanceMilesTo(justInsideUsc) < radius);
        assertTrue(USC.distanceMilesTo(justOutsideUsc) > radius);
        assertTrue(LAX.distanceMilesTo(justInsideLax) < radius);
        assertTrue(LAX.distanceMilesTo(justOutsideLax) > radius);

        add(ride("originOut", justOutsideUsc, justInsideLax, tomorrowAt(10, 0), 4));   // origin rejection
        add(ride("destOut", justInsideUsc, justOutsideLax, tomorrowAt(10, 0), 4));     // destination rejection
        add(ride("bothIn", justInsideUsc, justInsideLax, tomorrowAt(10, 0), 4));       // match

        search(criteria(radius).build());
        assertNull(error);
        assertEquals(Collections.singletonList("bothIn"), ids(results));

        // Exactly on the boundary is inside: rerun with the radius set to the exact origin distance.
        Location onBoundary = northOf(USC, 2, "Exactly on boundary");
        repository.rides.clear();
        add(ride("edge", onBoundary, LAX, tomorrowAt(10, 0), 4));
        search(criteria(USC.distanceMilesTo(onBoundary)).build());
        assertEquals(Collections.singletonList("edge"), ids(results));
    }

    // ----------------------------------------------------- WB13 radius validation

    /**
     * WB13: validate() for a nonpositive radius, allowed radii and a radius above
     * MAX_RADIUS_MILES. Invalid requests must stop before the repository is queried.
     */
    @Test
    public void wb13_radiusValidationStopsInvalidRequestsBeforeRepository() {
        assertEquals("The radius has to be more than 0 miles.", SearchService.validate(criteria(0).build()));
        assertEquals("The radius has to be more than 0 miles.", SearchService.validate(criteria(-1).build()));
        assertEquals("The radius has to be more than 0 miles.", SearchService.validate(criteria(Double.NaN).build()));
        assertNull(SearchService.validate(criteria(1).build()));
        assertNull(SearchService.validate(criteria(SearchService.MAX_RADIUS_MILES).build()));
        assertEquals("The radius can be at most 100 miles.",
                SearchService.validate(criteria(SearchService.MAX_RADIUS_MILES + 0.001).build()));
        assertEquals("The radius can be at most 100 miles.", SearchService.validate(criteria(101).build()));

        for (double bad : new double[]{0, 101}) {
            error = null;
            search(criteria(bad).build());
            assertNotNull("Radius " + bad + " should be rejected", error);
            assertTrue(error instanceof IllegalArgumentException);
        }
        assertEquals("Invalid radii must not query the repository", 0, repository.findActiveCalls);
        assertEquals(0, successCalls);

        search(criteria(100).build());
        assertEquals("A valid radius queries the repository once", 1, repository.findActiveCalls);
        assertEquals(1, successCalls);
    }

    // ------------------------------------------------ WB14 time window and seats

    /**
     * WB14: match() rejects a ride with too few open seats and accepts one inside the window;
     * inTimeWindow is checked directly for an outside time and both inclusive endpoints.
     */
    @Test
    public void wb14_seatFilterAndInclusiveTimeWindow() {
        Ride hasSeats = ride("ten", northOf(USC, 0.3, "Near USC"), northOf(LAX, 0.3, "Near LAX"),
                tomorrowAt(10, 0), 3);              // 2 open seats
        Ride full = ride("eleven", northOf(USC, 0.3, "Near USC"), northOf(LAX, 0.3, "Near LAX"),
                tomorrowAt(11, 0), 2);
        fill(full);                                 // 0 open seats
        assertEquals(2, hasSeats.getAvailableSeats());
        assertEquals(0, full.getAvailableSeats());

        SearchCriteria criteria = criteria(5)
                .date(TOMORROW)
                .timeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))
                .minSeats(1)
                .build();
        List<SearchResult> found = service.filterAndSort(Arrays.asList(hasSeats, full), criteria);
        assertEquals(Collections.singletonList("ten"), ids(found));

        // Control: with no seat requirement the full ride is in the window and matches too.
        SearchCriteria anySeats = criteria(5).date(TOMORROW)
                .timeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)).minSeats(0).build();
        assertEquals(2, service.filterAndSort(Arrays.asList(hasSeats, full), anySeats).size());

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(12, 0);
        assertFalse("Before the window", SearchService.inTimeWindow(LocalTime.of(8, 59), start, end));
        assertTrue("Start is inclusive", SearchService.inTimeWindow(start, start, end));
        assertTrue("End is inclusive", SearchService.inTimeWindow(end, start, end));
        assertFalse("After the window", SearchService.inTimeWindow(LocalTime.of(12, 1), start, end));
    }

    /** Extra WB14 coverage: the open-ended and past-midnight branches of inTimeWindow. */
    @Test
    public void wb14_extra_inTimeWindowOpenEndedAndOvernightBranches() {
        LocalTime noon = LocalTime.NOON;
        assertTrue("No limits", SearchService.inTimeWindow(noon, null, null));
        assertTrue("Only latest, before it", SearchService.inTimeWindow(noon, null, LocalTime.of(13, 0)));
        assertFalse("Only latest, after it", SearchService.inTimeWindow(noon, null, LocalTime.of(11, 0)));
        assertTrue("Only earliest, after it", SearchService.inTimeWindow(noon, LocalTime.of(11, 0), null));
        assertFalse("Only earliest, before it", SearchService.inTimeWindow(noon, LocalTime.of(13, 0), null));

        LocalTime tenPm = LocalTime.of(22, 0);
        LocalTime twoAm = LocalTime.of(2, 0);
        assertTrue("Overnight, late evening", SearchService.inTimeWindow(LocalTime.of(23, 30), tenPm, twoAm));
        assertTrue("Overnight, after midnight", SearchService.inTimeWindow(LocalTime.of(1, 0), tenPm, twoAm));
        assertFalse("Overnight, midday", SearchService.inTimeWindow(noon, tenPm, twoAm));
    }

    // --------------------------------------------------------- WB15 sort orders

    /**
     * WB15: every arm of the sort switch, plus the time tiebreaker when proximity is equal.
     * Proximity = origin distance + destination distance.
     */
    @Test
    public void wb15_sortCoversEveryOrderAndTimeTiebreaker() {
        Ride ten = ride("ten", USC, LAX, tomorrowAt(10, 0), 4);
        Ride eleven = ride("eleven", USC, LAX, tomorrowAt(11, 0), 4);
        Ride twelve = ride("twelve", USC, LAX, tomorrowAt(12, 0), 4);
        SearchResult farEarly = new SearchResult(ten, 2.0, 2.0);       // proximity 4.0
        SearchResult midMiddle = new SearchResult(eleven, 1.0, 1.0);   // proximity 2.0
        SearchResult closeLate = new SearchResult(twelve, 0.2, 0.3);   // proximity 0.5

        List<SearchResult> list = new ArrayList<>(Arrays.asList(midMiddle, closeLate, farEarly));

        SearchService.sort(list, RideSortOrder.EARLIEST_FIRST);
        assertEquals(Arrays.asList("ten", "eleven", "twelve"), ids(list));

        SearchService.sort(list, RideSortOrder.LATEST_FIRST);
        assertEquals(Arrays.asList("twelve", "eleven", "ten"), ids(list));

        SearchService.sort(list, RideSortOrder.PROXIMITY);
        assertEquals(Arrays.asList("twelve", "eleven", "ten"), ids(list));
        assertEquals(0.5, list.get(0).getProximityMiles(), 1e-9);

        // Equal proximity: PROXIMITY_THEN_TIME breaks the tie by earlier departure.
        Ride nine = ride("nine", USC, LAX, tomorrowAt(9, 0), 4);
        Ride thirteen = ride("thirteen", USC, LAX, tomorrowAt(13, 0), 4);
        SearchResult tieLate = new SearchResult(thirteen, 1.0, 1.0);   // proximity 2.0
        SearchResult tieEarly = new SearchResult(nine, 0.5, 1.5);      // proximity 2.0
        List<SearchResult> ties = new ArrayList<>(Arrays.asList(tieLate, closeLate, tieEarly));

        SearchService.sort(ties, RideSortOrder.PROXIMITY_THEN_TIME);
        assertEquals(Arrays.asList("twelve", "nine", "thirteen"), ids(ties));

        // PROXIMITY alone is stable, so the tie keeps its input order (13:00 before 9:00).
        List<SearchResult> stable = new ArrayList<>(Arrays.asList(tieLate, tieEarly));
        SearchService.sort(stable, RideSortOrder.PROXIMITY);
        assertEquals(Arrays.asList("thirteen", "nine"), ids(stable));
    }
}
