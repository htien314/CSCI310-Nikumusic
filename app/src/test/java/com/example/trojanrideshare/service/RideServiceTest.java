package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RideServiceTest {

    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long NOW = 1_000 * HOUR;
    private static final long DEPARTURE = NOW + 10 * HOUR;
    private static final String ORGANIZER = "organizer";

    private final Location usc = new Location("USC Village", 34.025, -118.285);
    private final Location lax = new Location("LAX", 33.942, -118.408);

    private FakeRideRepository repository;
    private long now;
    private RideService service;
    private Result result;

    @Before
    public void setUp() {
        repository = new FakeRideRepository();
        now = NOW;
        service = new RideService(repository, () -> now);
        result = new Result();
    }

    private static Timestamp at(long millis) {
        return new Timestamp(new Date(millis));
    }

    private void create(String title, Location origin, Location destination, long departure,
                        int maxRiders, long costCents) {
        service.createRide(title, origin, destination, at(departure), maxRiders, costCents,
                null, ORGANIZER, "Ada", result);
    }

    private Ride existingRide() {
        Result created = new Result();
        service.createRide("To LAX", usc, lax, at(DEPARTURE), 4, 6_000, null, ORGANIZER, "Ada", created);
        return created.ride;
    }

    private void assertRejected() {
        assertNull(result.ride);
        assertTrue(result.error instanceof RideRuleException);
    }

    @Test
    public void createRide_savesValidRide() {
        create("  To LAX ", usc, lax, DEPARTURE, 4, 6_000);

        assertNull(result.error);
        assertEquals("To LAX", result.ride.getTitle());
        assertEquals(ActivityStatus.ON_TIME, result.ride.getActivityStatus());
        assertEquals(CapacityStatus.OPEN, result.ride.getCapacityStatus());
        assertTrue(result.ride.isOrganizer(ORGANIZER));
        assertSame(result.ride, repository.rides.get(result.ride.getId()));
    }

    @Test
    public void createRide_acceptsRiderLimitBoundaries() {
        create("To LAX", usc, lax, DEPARTURE, 2, 6_000);
        assertNull(result.error);

        create("To LAX", usc, lax, DEPARTURE, 6, 6_000);
        assertNull(result.error);
    }

    @Test
    public void createRide_rejectsRiderLimitOutsideRange() {
        create("To LAX", usc, lax, DEPARTURE, 1, 6_000);
        assertRejected();

        create("To LAX", usc, lax, DEPARTURE, 7, 6_000);
        assertRejected();
    }

    @Test
    public void createRide_rejectsInvalidInput() {
        create("  ", usc, lax, DEPARTURE, 4, 6_000);
        assertRejected();

        create("To LAX", null, lax, DEPARTURE, 4, 6_000);
        assertRejected();

        create("To LAX", usc, new Location("", 0, 0), DEPARTURE, 4, 6_000);
        assertRejected();

        create("To LAX", usc, lax, NOW, 4, 6_000);
        assertRejected();

        create("To LAX", usc, lax, DEPARTURE, 4, 0);
        assertRejected();

        assertTrue(repository.rides.isEmpty());
    }

    @Test
    public void markDelayed_keepsOriginalTimeAndStoresNewTime() {
        Ride ride = existingRide();

        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), result);

        assertEquals(ActivityStatus.DELAYED, result.ride.getActivityStatus());
        assertEquals(at(DEPARTURE), result.ride.getDepartureTime());
        assertEquals(at(DEPARTURE + HOUR), result.ride.getCurrentDepartureTime());
    }

    @Test
    public void markDelayed_requiresTimeAfterOriginal() {
        Ride ride = existingRide();

        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE), result);
        assertRejected();

        service.markDelayed(ride.getId(), ORGANIZER, null, result);
        assertRejected();

        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
    }

    @Test
    public void markDelayed_rejectsPastTime() {
        Ride ride = existingRide();
        now = DEPARTURE + 2 * HOUR;

        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), result);

        assertRejected();
    }

    @Test
    public void markOnTime_clearsDelayedTime() {
        Ride ride = existingRide();
        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), new Result());

        service.markOnTime(ride.getId(), ORGANIZER, result);

        assertEquals(ActivityStatus.ON_TIME, result.ride.getActivityStatus());
        assertNull(result.ride.getDelayedDepartureTime());
        assertEquals(at(DEPARTURE), result.ride.getCurrentDepartureTime());
    }

    @Test
    public void markOnTime_rejectedAfterOriginalTimePassed() {
        Ride ride = existingRide();
        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), new Result());
        now = DEPARTURE;

        service.markOnTime(ride.getId(), ORGANIZER, result);

        assertRejected();
        assertEquals(ActivityStatus.DELAYED, ride.getActivityStatus());
    }

    @Test
    public void cancelRide_allowedAtExactlyThreeHours() {
        Ride ride = existingRide();
        now = DEPARTURE - 3 * HOUR;

        service.cancelRide(ride.getId(), ORGANIZER, result);

        assertEquals(ActivityStatus.CANCELLED, result.ride.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, result.ride.getCapacityStatus());
    }

    @Test
    public void cancelRide_rejectedInsideThreeHours() {
        Ride ride = existingRide();
        now = DEPARTURE - 3 * HOUR + 1;

        service.cancelRide(ride.getId(), ORGANIZER, result);

        assertRejected();
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
    }

    @Test
    public void cancelRide_cutoffUsesOriginalTimeWhenDelayed() {
        Ride ride = existingRide();
        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + 5 * HOUR), new Result());
        now = DEPARTURE - HOUR;

        service.cancelRide(ride.getId(), ORGANIZER, result);

        assertRejected();
    }

    @Test
    public void organizerActions_rejectedForOtherUsers() {
        Ride ride = existingRide();

        service.markDelayed(ride.getId(), "rider", at(DEPARTURE + HOUR), result);
        assertRejected();

        service.markOnTime(ride.getId(), "rider", result);
        assertRejected();

        service.cancelRide(ride.getId(), "rider", result);
        assertRejected();

        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
    }

    @Test
    public void finalStatuses_cannotBeChanged() {
        Ride ride = existingRide();
        service.cancelRide(ride.getId(), ORGANIZER, new Result());

        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), result);
        assertRejected();

        service.markOnTime(ride.getId(), ORGANIZER, result);
        assertRejected();

        service.cancelRide(ride.getId(), ORGANIZER, result);
        assertRejected();

        assertEquals(ActivityStatus.CANCELLED, ride.getActivityStatus());
    }

    @Test
    public void checkCanComplete_allowsOrganizerAloneAtDeparture() {
        Ride ride = existingRide();
        now = DEPARTURE;

        service.checkCanComplete(ride, ORGANIZER);
    }

    @Test(expected = RideRuleException.class)
    public void checkCanComplete_rejectsBeforeDeparture() {
        Ride ride = existingRide();
        now = DEPARTURE - 1;

        service.checkCanComplete(ride, ORGANIZER);
    }

    @Test(expected = RideRuleException.class)
    public void checkCanComplete_usesDelayedTime() {
        Ride ride = existingRide();
        service.markDelayed(ride.getId(), ORGANIZER, at(DEPARTURE + HOUR), new Result());
        now = DEPARTURE;

        service.checkCanComplete(ride, ORGANIZER);
    }

    @Test(expected = RideRuleException.class)
    public void checkCanComplete_rejectsNonOrganizer() {
        Ride ride = existingRide();
        now = DEPARTURE;

        service.checkCanComplete(ride, "rider");
    }

    @Test
    public void getRide_reportsMissingRide() {
        service.getRide("missing", result);

        assertTrue(result.error instanceof NoSuchElementException);
    }

    private static class Result implements Callback<Ride> {
        Ride ride;
        Exception error;

        @Override
        public void onSuccess(Ride ride) {
            this.ride = ride;
            this.error = null;
        }

        @Override
        public void onError(Exception e) {
            this.ride = null;
            this.error = e;
        }
    }
}
