package com.example.trojanrideshare.ui.ride;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.ui.common.UiState;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.Assert.*;

public class RideDetailsViewModelTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, LOS_ANGELES);
    private static final ZonedDateTime DEPARTURE = NOW.plusHours(10);
    private static final String ORGANIZER = "organizer";

    @Rule
    public InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private FakeRideRepository repository;
    private Ride ride;
    private RideDetailsViewModel viewModel;

    private static Timestamp at(ZonedDateTime time) {
        return new Timestamp(Date.from(time.toInstant()));
    }

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        ride = Ride.createNew("To LAX", new Location("USC Village", 34.025, -118.285),
                new Location("LAX", 33.942, -118.408), at(DEPARTURE), 4, 6_000, null,
                ORGANIZER, "Ada", at(NOW));
        ride.setId("ride");
        repository.rides.put("ride", ride);
        RideService service = new RideService(repository, () -> NOW.toInstant().toEpochMilli());
        viewModel = new RideDetailsViewModel(service);
    }

    private void assertNotLoading() {
        assertEquals(UiState.Status.SUCCESS, viewModel.getState().getValue().getStatus());
    }

    @Test
    public void load_showsRide() {
        viewModel.load("ride");

        assertSame(ride, viewModel.getRide().getValue());
        assertNotLoading();
    }

    @Test
    public void load_reportsMissingRide() {
        viewModel.load("missing");

        assertTrue(viewModel.getState().getValue().isError());
        assertEquals("Ride not found.", viewModel.getState().getValue().getMessage());
        assertNull(viewModel.getRide().getValue());
    }

    @Test
    public void load_reportsMissingRideId() {
        viewModel.load(null);

        assertTrue(viewModel.getState().getValue().isError());
    }

    @Test
    public void markDelayed_readsTimeAsLosAngelesTime() {
        viewModel.load("ride");

        viewModel.markDelayed(ORGANIZER, LocalDate.of(2026, 11, 2), LocalTime.of(9, 30));

        Ride updated = viewModel.getRide().getValue();
        assertEquals(ActivityStatus.DELAYED, updated.getActivityStatus());
        assertEquals(at(ZonedDateTime.of(2026, 11, 2, 9, 30, 0, 0, LOS_ANGELES)), updated.getCurrentDepartureTime());
        assertNotLoading();
    }

    @Test
    public void markOnTime_restoresOriginalTime() {
        viewModel.load("ride");
        viewModel.markDelayed(ORGANIZER, LocalDate.of(2026, 11, 2), LocalTime.of(9, 30));

        viewModel.markOnTime(ORGANIZER);

        assertEquals(ActivityStatus.ON_TIME, viewModel.getRide().getValue().getActivityStatus());
        assertEquals(at(DEPARTURE), viewModel.getRide().getValue().getCurrentDepartureTime());
    }

    @Test
    public void cancelRide_updatesStatuses() {
        viewModel.load("ride");

        viewModel.cancelRide(ORGANIZER);

        assertEquals(ActivityStatus.CANCELLED, viewModel.getRide().getValue().getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, viewModel.getRide().getValue().getCapacityStatus());
    }

    @Test
    public void rejectedAction_reportsReasonAndKeepsRideOnScreen() {
        viewModel.load("ride");

        viewModel.cancelRide("someone else");

        assertEquals("Only the organizer can do this.", viewModel.getActionError().getValue());
        assertSame(ride, viewModel.getRide().getValue());
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
        assertNotLoading();

        viewModel.markOnTime(ORGANIZER);
        assertNull(viewModel.getActionError().getValue());
    }

    @Test
    public void actions_doNothingBeforeRideLoads() {
        viewModel.cancelRide(ORGANIZER);

        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
    }
}
