package com.example.trojanrideshare.ui.search;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.trojanrideshare.data.FakeLocationGateway;
import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.service.SearchResult;
import com.example.trojanrideshare.service.SearchService;
import com.example.trojanrideshare.ui.common.UiState;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class SearchViewModelTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles"));

    // Same coordinates FakeLocationGateway returns for "usc" and "lax"
    private static final Location USC_VILLAGE = new Location("USC Village", 34.0256, -118.2850);
    private static final Location LAX = new Location("LAX", 33.9416, -118.4085);

    @Rule
    public InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private FakeRideRepository repository;
    private SearchViewModel viewModel;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        SearchService service = new SearchService(repository, () -> NOW.toInstant().toEpochMilli());
        viewModel = new SearchViewModel(service, new FakeLocationGateway());
    }

    private void addRide(String id, ZonedDateTime departure) {
        Ride ride = Ride.createNew(id, USC_VILLAGE, LAX, new Timestamp(Date.from(departure.toInstant())), 4,
                3000L, null, "organizer", "Ada", new Timestamp(Date.from(NOW.toInstant())));
        ride.setId(id);
        repository.rides.put(id, ride);
    }

    private void assertError(String message) {
        UiState state = viewModel.getState().getValue();
        assertTrue(state.isError());
        assertEquals(message, state.getMessage());
    }

    @Test
    public void search_showsMatchingRides() {
        addRide("ride", NOW.plusDays(1));
        viewModel.search("usc", "lax", "5", false);

        assertEquals(UiState.Status.SUCCESS, viewModel.getState().getValue().getStatus());
        List<SearchResult> results = viewModel.getResults().getValue();
        assertEquals(1, results.size());
        assertEquals("ride", results.get(0).getRide().getId());
    }

    @Test
    public void search_noMatchesGivesEmptyList() {
        viewModel.search("usc", "lax", "5", false);
        assertNotNull(viewModel.getResults().getValue());
        assertTrue(viewModel.getResults().getValue().isEmpty());
    }

    @Test
    public void search_usesDateFilter() {
        addRide("tomorrow", NOW.plusDays(1));
        addRide("nextWeek", NOW.plusDays(7));
        viewModel.setDate(LocalDate.of(2026, 11, 8));
        viewModel.search("usc", "lax", "5", false);

        List<SearchResult> results = viewModel.getResults().getValue();
        assertEquals(1, results.size());
        assertEquals("nextWeek", results.get(0).getRide().getId());
    }

    @Test
    public void search_requiresBothLocations() {
        viewModel.search("  ", "lax", "5", false);
        assertError("Enter a pickup location and a destination.");
        assertNull(viewModel.getResults().getValue());
    }

    @Test
    public void search_rejectsRadiusThatIsNotANumber() {
        viewModel.search("usc", "lax", "far", false);
        assertError("Enter a radius in miles.");
    }

    @Test
    public void search_passesOnServiceValidation() {
        viewModel.search("usc", "lax", "0", false);
        assertError("The radius has to be more than 0 miles.");
    }

    @Test
    public void search_reportsUnknownPlace() {
        viewModel.search("Atlantis", "lax", "5", false);
        assertError("Couldn't find \"Atlantis\".");
    }

    @Test
    public void setSortOrder_resortsCurrentResults() {
        addRide("later", NOW.plusDays(2));
        addRide("sooner", NOW.plusDays(1));
        viewModel.setSortOrder(RideSortOrder.LATEST_FIRST);
        viewModel.search("usc", "lax", "5", false);
        assertEquals("later", viewModel.getResults().getValue().get(0).getRide().getId());

        viewModel.setSortOrder(RideSortOrder.EARLIEST_FIRST);
        assertEquals("sooner", viewModel.getResults().getValue().get(0).getRide().getId());
    }

    @Test
    public void clearDateAndTime_resetsFilters() {
        viewModel.setDate(LocalDate.of(2026, 11, 8));
        viewModel.setEarliestTime(java.time.LocalTime.of(8, 0));
        viewModel.setLatestTime(java.time.LocalTime.of(10, 0));
        viewModel.clearDateAndTime();
        assertNull(viewModel.getDate().getValue());
        assertNull(viewModel.getEarliestTime().getValue());
        assertNull(viewModel.getLatestTime().getValue());
    }
}
