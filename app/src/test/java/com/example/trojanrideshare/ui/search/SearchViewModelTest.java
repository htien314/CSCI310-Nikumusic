package com.example.trojanrideshare.ui.search;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.trojanrideshare.data.FakeLocationGateway;
import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.service.SearchResult;
import com.example.trojanrideshare.service.SearchService;
import com.example.trojanrideshare.ui.common.UiState;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class SearchViewModelTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles"));

    // Same coordinates FakeLocationGateway returns for "usc" and "lax"
    private static final Location USC_VILLAGE = new Location("USC Village", 34.0256, -118.2850);
    private static final Location LAX = new Location("LAX", 33.9416, -118.4085);
    private static final Location UNION_STATION = new Location("Union Station", 34.0562, -118.2365);

    @Rule
    public InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private FakeRideRepository repository;
    private SearchService service;
    private SearchViewModel viewModel;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        service = new SearchService(repository, () -> NOW.toInstant().toEpochMilli());
        viewModel = new SearchViewModel(service, new FakeLocationGateway());
    }

    private Ride addRide(String id, Location origin, Location destination, ZonedDateTime departure) {
        Ride ride = Ride.createNew(id, origin, destination, new Timestamp(Date.from(departure.toInstant())), 4,
                3000L, null, "organizer-" + id, "Ada", new Timestamp(Date.from(NOW.toInstant())));
        ride.setId(id);
        repository.rides.put(id, ride);
        return ride;
    }

    private Ride addRide(String id, ZonedDateTime departure) {
        return addRide(id, USC_VILLAGE, LAX, departure);
    }

    private void search(String pickup, String destination, String radius) {
        viewModel.search(null, pickup, null, destination, radius, 0, "me");
    }

    private List<String> resultIds() {
        List<String> ids = new ArrayList<>();
        for (SearchResult result : viewModel.getResults().getValue()) {
            ids.add(result.getRide().getId());
        }
        return ids;
    }

    private void assertError(String message) {
        UiState state = viewModel.getState().getValue();
        assertTrue(state.isError());
        assertEquals(message, state.getMessage());
    }

    // ---- browsing ----

    @Test
    public void start_browsesUpcomingRidesOnce() {
        addRide("ride", NOW.plusDays(1));
        viewModel.start("me");
        assertFalse(viewModel.getSearching().getValue());
        assertEquals(1, viewModel.getResults().getValue().size());

        viewModel.start("me");
        assertEquals("Second start doesn't add another listener", 1, repository.listeners.size());
    }

    @Test
    public void browse_hidesTheUsersOwnRides() {
        addRide("mine", NOW.plusDays(1));
        addRide("theirs", NOW.plusDays(2));
        viewModel.start("organizer-mine");
        assertEquals("theirs", resultIds().get(0));
        assertEquals(1, resultIds().size());
    }

    @Test
    public void browse_updatesLiveWhenAnotherUserPostsARide() {
        viewModel.start("me");
        assertTrue(viewModel.getResults().getValue().isEmpty());

        addRide("new", NOW.plusDays(1));
        repository.notifyListeners();
        assertEquals(1, viewModel.getResults().getValue().size());
    }

    // ---- searching ----

    @Test
    public void start_replacesPreviousAccountsResultsAndListener() {
        addRide("alice", NOW.plusDays(1));
        addRide("bob", NOW.plusDays(2));
        viewModel.start("organizer-alice");
        assertEquals("bob", resultIds().get(0));

        viewModel.start("organizer-bob");
        assertEquals("alice", resultIds().get(0));
        assertEquals(1, repository.listeners.size());
    }

    @Test
    public void start_retriesAfterSigningInFollowingPermissionDenial() {
        repository.findActiveError = new SecurityException("signed out");
        viewModel.start(null);
        assertTrue(viewModel.getState().getValue().isError());

        repository.findActiveError = null;
        addRide("ride", NOW.plusDays(1));
        viewModel.start("me");
        assertEquals(UiState.Status.SUCCESS, viewModel.getState().getValue().getStatus());
        assertEquals("ride", resultIds().get(0));
        assertEquals(1, repository.listeners.size());
    }

    @Test
    public void clearingViewModelIgnoresPendingLocationLookup() {
        ManualGateway gateway = new ManualGateway();
        viewModel = new SearchViewModel(service, gateway);
        viewModel.search(USC_VILLAGE, "", null, "lax", "5", 1, "me");
        viewModel.onCleared();
        gateway.pending.get(0).onSuccess(LAX);
        assertTrue(repository.listeners.isEmpty());
    }

    @Test
    public void search_showsMatchingRides() {
        addRide("ride", NOW.plusDays(1));
        search("usc", "lax", "5");

        assertTrue(viewModel.getSearching().getValue());
        assertEquals(UiState.Status.SUCCESS, viewModel.getState().getValue().getStatus());
        assertEquals("ride", resultIds().get(0));
    }

    @Test
    public void search_usesPickedPlacesWithoutLookingThemUp() {
        addRide("ride", NOW.plusDays(1));
        LocationGateway failing = new LocationGateway() {
            @Override
            public void lookup(String query, Callback<Location> callback) {
                fail("Picked places shouldn't be looked up again");
            }
        };
        viewModel = new SearchViewModel(service, failing);
        viewModel.search(USC_VILLAGE, "anything", LAX, "anything", "5", 0, "me");
        assertEquals("ride", resultIds().get(0));
    }

    @Test
    public void search_replacesBrowseListener() {
        viewModel.start("me");
        search("usc", "lax", "5");
        assertEquals("Only the search listener is left", 1, repository.listeners.size());
    }

    @Test
    public void search_resultsUpdateWhenRideIsCancelled() {
        Ride ride = addRide("ride", NOW.plusDays(1));
        search("usc", "lax", "5");
        assertEquals(1, resultIds().size());

        ride.setActivityStatus(ActivityStatus.CANCELLED);
        repository.notifyListeners();
        assertTrue(resultIds().isEmpty());
    }

    @Test
    public void search_usesDateFilter() {
        addRide("tomorrow", NOW.plusDays(1));
        addRide("nextWeek", NOW.plusDays(7));
        viewModel.setDate(LocalDate.of(2026, 11, 8));
        search("usc", "lax", "5");
        assertEquals(1, resultIds().size());
        assertEquals("nextWeek", resultIds().get(0));
    }

    @Test
    public void search_usesSeatFilter() {
        Ride fullRide = addRide("full", NOW.plusDays(1));
        while (fullRide.getAvailableSeats() > 0) {
            fullRide.getParticipants().add(new com.example.trojanrideshare.model.Participant(
                    "r" + fullRide.getParticipants().size(), "R", null));
        }
        viewModel.search(null, "usc", null, "lax", "5", 1, "me");
        assertTrue(resultIds().isEmpty());
    }

    @Test
    public void search_requiresBothLocations() {
        search("  ", "lax", "5");
        assertError("Enter a pickup location and a destination.");
    }

    @Test
    public void search_rejectsRadiusThatIsNotANumber() {
        search("usc", "lax", "far");
        assertError("Enter a radius in miles.");
    }

    @Test
    public void search_passesOnServiceValidation() {
        search("usc", "lax", "0");
        assertError("The radius has to be more than 0 miles.");
    }

    @Test
    public void search_reportsUnknownPlace() {
        search("Atlantis", "lax", "5");
        assertError("Couldn't find \"Atlantis\".");
    }

    @Test
    public void search_reportsLoadFailure() {
        repository.findActiveError = new SecurityException("PERMISSION_DENIED");
        search("usc", "lax", "5");
        assertError("You don't have permission to see rides. Make sure you're logged in.");
    }

    @Test
    public void slowOldLookupDoesNotOverwriteNewerSearch() {
        addRide("toLax", USC_VILLAGE, LAX, NOW.plusDays(1));
        addRide("toUnion", USC_VILLAGE, UNION_STATION, NOW.plusDays(1));
        ManualGateway gateway = new ManualGateway();
        viewModel = new SearchViewModel(service, gateway);

        // First search's destination lookup is still pending when the second search starts
        viewModel.search(USC_VILLAGE, "", null, "lax", "2", 0, "me");
        viewModel.search(USC_VILLAGE, "", UNION_STATION, "", "2", 0, "me");
        assertEquals("toUnion", resultIds().get(0));

        gateway.pending.get(0).onSuccess(LAX);
        assertEquals("Stale answer ignored", "toUnion", resultIds().get(0));
        assertEquals(1, repository.listeners.size());
    }

    // ---- sorting and filters ----

    @Test
    public void setSortOrder_resortsCurrentResults() {
        addRide("later", NOW.plusDays(2));
        addRide("sooner", NOW.plusDays(1));
        viewModel.setSortOrder(RideSortOrder.LATEST_FIRST);
        search("usc", "lax", "5");
        assertEquals("later", resultIds().get(0));

        viewModel.setSortOrder(RideSortOrder.EARLIEST_FIRST);
        assertEquals("sooner", resultIds().get(0));
    }

    @Test
    public void liveUpdateKeepsTheCurrentSortOrder() {
        addRide("later", NOW.plusDays(2));
        addRide("sooner", NOW.plusDays(1));
        viewModel.setSortOrder(RideSortOrder.LATEST_FIRST);
        search("usc", "lax", "5");

        viewModel.setSortOrder(RideSortOrder.EARLIEST_FIRST);
        repository.notifyListeners();
        assertEquals("sooner", resultIds().get(0));
    }

    @Test
    public void clearDateAndTime_resetsFilters() {
        viewModel.setDate(LocalDate.of(2026, 11, 8));
        viewModel.setEarliestTime(LocalTime.of(8, 0));
        viewModel.setLatestTime(LocalTime.of(10, 0));
        viewModel.clearDateAndTime();
        assertNull(viewModel.getDate().getValue());
        assertNull(viewModel.getEarliestTime().getValue());
        assertNull(viewModel.getLatestTime().getValue());
    }

    /** Holds lookups until the test answers them. */
    private static class ManualGateway implements LocationGateway {
        final List<Callback<Location>> pending = new ArrayList<>();

        @Override
        public void lookup(String query, Callback<Location> callback) {
            pending.add(callback);
        }
    }
}
