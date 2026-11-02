package com.example.trojanrideshare.ui.ride;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.data.FakeLocationGateway;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.ui.common.UiState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class CreateRideViewModelTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles"));

    @Rule
    public InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private FakeRideRepository repository;
    private CreateRideViewModel viewModel;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        RideService service = new RideService(repository, () -> NOW.toInstant().toEpochMilli());
        viewModel = new CreateRideViewModel(service, new FakeLocationGateway());
        viewModel.setDate(LocalDate.of(2026, 11, 2));
        viewModel.setTime(LocalTime.of(9, 30));
    }

    private void create(String origin, String cost) {
        viewModel.createRide("To LAX", origin, "lax", 4, cost, "  ", "user", "Ada");
    }

    private void assertError(String message) {
        UiState state = viewModel.getState().getValue();
        assertTrue(state.isError());
        assertEquals(message, state.getMessage());
        assertNull(viewModel.getCreatedRideId().getValue());
        assertTrue(repository.rides.isEmpty());
    }

    @Test
    public void choosingDateOrTime_clearsOldError() {
        create("USC", "not money");
        assertTrue(viewModel.getState().getValue().isError());

        viewModel.setDate(LocalDate.of(2026, 11, 3));
        assertEquals(UiState.Status.IDLE, viewModel.getState().getValue().getStatus());

        create("USC", "not money");
        assertTrue(viewModel.getState().getValue().isError());

        viewModel.setTime(LocalTime.of(10, 0));
        assertEquals(UiState.Status.IDLE, viewModel.getState().getValue().getStatus());
    }

    @Test
    public void createRide_savesRideAndReportsItsId() {
        create("USC", "60.50");

        Ride ride = repository.rides.get(viewModel.getCreatedRideId().getValue());
        assertEquals(UiState.Status.SUCCESS, viewModel.getState().getValue().getStatus());
        assertEquals("USC Village", ride.getOrigin().getAddress());
        assertEquals("LAX", ride.getDestination().getAddress());
        assertEquals(6_050, ride.getExpectedCostCents());
        assertEquals(4, ride.getMaxRiders());
        assertNull(ride.getNote());
    }

    @Test
    public void createRide_readsDepartureAsLosAngelesTime() {
        create("USC", "60");

        Ride ride = repository.rides.get(viewModel.getCreatedRideId().getValue());
        // 9:30 AM in Los Angeles on Nov 2, 2026 (after clocks change) is 17:30 UTC.
        assertEquals(1_793_640_600L, ride.getDepartureTime().getSeconds());
    }

    @Test
    public void createRide_reportsCostError() {
        create("USC", "0");

        assertError("Cost must be more than $0.");
    }

    @Test
    public void createRide_requiresDateAndTime() {
        viewModel.setTime(null);

        create("USC", "60");

        assertError("Choose a date and time.");
    }

    @Test
    public void createRide_reportsUnknownLocation() {
        create("Mars", "60");

        assertError("Couldn't find \"Mars\".");
    }

    @Test
    public void createRide_reportsServiceRuleError() {
        viewModel.createRide(" ", "USC", "LAX", 4, "60", null, "user", "Ada");

        assertError("Enter a title.");
    }

    @Test
    public void onRideOpened_resetsForm() {
        create("USC", "60");

        viewModel.onRideOpened();

        assertNull(viewModel.getCreatedRideId().getValue());
        assertNull(viewModel.getDate().getValue());
        assertNull(viewModel.getTime().getValue());
        assertEquals(UiState.Status.IDLE, viewModel.getState().getValue().getStatus());
    }
}
