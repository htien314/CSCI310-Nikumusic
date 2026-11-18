package com.example.trojanrideshare;

import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.MembershipTransition;
import com.example.trojanrideshare.service.RideRuleException;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.MoneyParser;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * WB06–WB10 (Ada, ride management): JUnit white-box tests for {@link RideService}.
 *
 * <p>Each test drives specific decisions in RideService with a fixed clock and an in-memory
 * repository, so every time rule is checked exactly at its boundary. The production cancel path
 * also runs {@link MembershipTransition#CANCEL_RIDE}, so WB10 checks that its cutoff matches.
 */
public class RideManagementWhiteBoxTest {

    private static final long HOUR = 3_600_000L;
    /** Fixed "now": 2026-11-20 18:00 UTC. */
    private static final long NOW = 1_795_543_200_000L;
    private static final long TOMORROW = NOW + 24 * HOUR;
    private static final String ORGANIZER = "organizer";
    private static final Location USC = new Location("University of Southern California", 34.0224, -118.2851);
    private static final Location LAX = new Location("Los Angeles International Airport", 33.9416, -118.4085);

    /** Records how many rides reach the repository, so rejected input can be shown to write nothing. */
    private static final class CountingRideRepository extends FakeRideRepository {
        int createCalls;

        @Override
        public void create(Ride ride, Callback<Ride> callback) {
            createCalls++;
            super.create(ride, callback);
        }
    }

    /** Keeps the last outcome of a callback. */
    private static final class Outcome implements Callback<Ride> {
        Ride ride;
        Exception error;

        @Override public void onSuccess(Ride result) { ride = result; }
        @Override public void onError(Exception e) { error = e; }

        void assertRejectedWith(String message) {
            assertNull("Expected a rejection, but the call succeeded.", ride);
            assertTrue("Expected RideRuleException, got " + error, error instanceof RideRuleException);
            assertEquals(message, error.getMessage());
        }
    }

    private CountingRideRepository repository;
    private RideService service;

    @Before
    public void setUp() {
        repository = new CountingRideRepository();
        service = new RideService(repository, () -> NOW);
    }

    private static Timestamp at(long millis) {
        return new Timestamp(new Date(millis));
    }

    private Outcome create(long departure, int maxRiders, long costCents) {
        Outcome outcome = new Outcome();
        service.createRide("  USC to LAX  ", USC, LAX, at(departure), maxRiders, costCents,
                "Meet at the Tutor Campus Center", ORGANIZER, "Tommy Trojan", outcome);
        return outcome;
    }

    /** Puts a saved ride owned by ORGANIZER, departing at {@code departure}, in the repository. */
    private Ride savedRide(long departure) {
        Outcome outcome = create(departure, 4, 4000);
        assertNotNull(outcome.ride);
        return outcome.ride;
    }

    /**
     * WB06: the success path of createRide, from the "$40" the user types to the saved document.
     * Branches covered: every validation guard taken on its passing side, then repository.create.
     */
    @Test
    public void wb06_createRideSavesOneValidRideWithOrganizerAsFirstMember() {
        long costCents = MoneyParser.parsePositiveCents("40");
        assertEquals(4000, costCents);

        Outcome outcome = create(TOMORROW, 4, costCents);

        assertNull(outcome.error);
        assertEquals(1, repository.createCalls);
        Ride ride = outcome.ride;
        assertSame(ride, repository.rides.get(ride.getId()));
        assertEquals("USC to LAX", ride.getTitle());
        assertEquals(USC.getAddress(), ride.getOrigin().getAddress());
        assertEquals(LAX.getAddress(), ride.getDestination().getAddress());
        assertEquals(TOMORROW, ride.getDepartureTime().toDate().getTime());
        assertNull(ride.getDelayedDepartureTime());
        assertEquals(4, ride.getMaxRiders());
        assertEquals(4000, ride.getExpectedCostCents());
        assertEquals("Meet at the Tutor Campus Center", ride.getNote());
        assertEquals(ORGANIZER, ride.getOrganizerId());
        assertEquals("Tommy Trojan", ride.getOrganizerName());
        assertEquals(Collections.singletonList(ORGANIZER), ride.getParticipantIds());
        assertEquals(NOW, ride.getParticipants().get(0).getAcceptedAt().toDate().getTime());
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
        assertEquals(CapacityStatus.OPEN, ride.getCapacityStatus());
        assertEquals(3, ride.getAvailableSeats());
    }

    /**
     * WB06 (extra, for branch coverage): each remaining createRide guard on its rejecting side.
     * Every case changes one field of the valid WB06 input, and none may reach the repository.
     */
    @Test
    public void wb06_extra_createRideRejectsEachInvalidFieldWithoutSaving() {
        Timestamp departure = at(TOMORROW);
        Location blank = new Location(" ", 34.0, -118.0);
        Location offMap = new Location("Nowhere", 91, 0);
        String longText = new String(new char[1001]).replace('\0', 'x');

        Object[][] cases = {
                {"USC to LAX", USC, LAX, 4000L, null, null, "You must be logged in to create a ride."},
                {"USC to LAX", USC, LAX, 4000L, null, "", "You must be logged in to create a ride."},
                {null, USC, LAX, 4000L, null, ORGANIZER, "Enter a title."},
                {"   ", USC, LAX, 4000L, null, ORGANIZER, "Enter a title."},
                {longText.substring(0, 101), USC, LAX, 4000L, null, ORGANIZER, "Title must be at most 100 characters."},
                {"USC to LAX", USC, LAX, 4000L, longText + longText, ORGANIZER, "Note must be at most 2000 characters."},
                {"USC to LAX", null, LAX, 4000L, null, ORGANIZER, "Choose a valid origin."},
                {"USC to LAX", blank, LAX, 4000L, null, ORGANIZER, "Choose a valid origin."},
                {"USC to LAX", USC, null, 4000L, null, ORGANIZER, "Choose a valid destination."},
                {"USC to LAX", USC, offMap, 4000L, null, ORGANIZER, "Choose a valid destination."},
                {"USC to LAX", new Location(longText, 34.0, -118.0), LAX, 4000L, null, ORGANIZER,
                        "Location addresses must be at most 1000 characters."},
                {"USC to LAX", USC, new Location(longText, 34.0, -118.0), 4000L, null, ORGANIZER,
                        "Location addresses must be at most 1000 characters."},
                {"USC to LAX", USC, LAX, 0L, null, ORGANIZER, "Expected cost must be more than $0."},
                {"USC to LAX", USC, LAX, -1L, null, ORGANIZER, "Expected cost must be more than $0."},
        };
        for (Object[] c : cases) {
            Outcome outcome = new Outcome();
            service.createRide((String) c[0], (Location) c[1], (Location) c[2], departure, 4, (Long) c[3],
                    (String) c[4], (String) c[5], "Tommy Trojan", outcome);
            outcome.assertRejectedWith((String) c[6]);
        }
        assertEquals(0, repository.createCalls);

        // Boundary controls on the passing side: exactly 100 title characters and 2000 note characters.
        Outcome limits = new Outcome();
        service.createRide(longText.substring(0, 100), USC, LAX, departure, 4, 1, longText.substring(0, 1000)
                + longText.substring(0, 1000), ORGANIZER, "Tommy Trojan", limits);
        assertNull(limits.error);
        assertEquals(1, limits.ride.getExpectedCostCents());
        assertEquals(1, repository.createCalls);
    }

    /**
     * WB07: both sides of the capacity range check (maxRiders &lt; 2, maxRiders &gt; 6) and the
     * valid continuation. Invalid capacities must stop before repository.create is called.
     */
    @Test
    public void wb07_capacityAcceptsTwoAndSixAndRejectsOneAndSevenWithoutSaving() {
        String message = "Maximum riders must be between 2 and 6.";

        create(TOMORROW, 1, 4000).assertRejectedWith(message);
        create(TOMORROW, 7, 4000).assertRejectedWith(message);
        assertEquals("Invalid capacities must not reach the repository.", 0, repository.createCalls);

        Outcome two = create(TOMORROW, 2, 4000);
        Outcome six = create(TOMORROW, 6, 4000);
        assertEquals(2, two.ride.getMaxRiders());
        assertEquals(1, two.ride.getAvailableSeats());
        assertEquals(6, six.ride.getMaxRiders());
        assertEquals(5, six.ride.getAvailableSeats());
        assertEquals(2, repository.createCalls);
    }

    /**
     * WB08: the "departure &lt;= now" guard with a fixed clock at T. T−1 ms and T exactly take the
     * rejecting branch; T+1 ms takes the passing branch and saves.
     */
    @Test
    public void wb08_departureMustBeStrictlyAfterNow() {
        String message = "Departure time must be in the future.";

        create(NOW - 1, 4, 4000).assertRejectedWith(message);
        create(NOW, 4, 4000).assertRejectedWith(message);
        Outcome missing = new Outcome();
        service.createRide("USC to LAX", USC, LAX, null, 4, 4000, null, ORGANIZER, "Tommy Trojan", missing);
        missing.assertRejectedWith(message);
        assertEquals(0, repository.createCalls);

        Outcome justAfter = create(NOW + 1, 4, 4000);
        assertNull(justAfter.error);
        assertEquals(NOW + 1, justAfter.ride.getDepartureTime().toDate().getTime());
        assertEquals(1, repository.createCalls);
    }

    /**
     * WB09: markDelayed's update branch and its rejection branches. A new time equal to the
     * original is rejected and leaves the ride unchanged; a later time sets Delayed and the new time
     * while keeping the original. Non-organizers and finished rides are refused.
     */
    @Test
    public void wb09_markDelayedStoresLaterTimeAndKeepsOriginal() {
        Ride ride = savedRide(TOMORROW);
        String timeRule = "New departure time must be after the original time and in the future.";

        Outcome sameTime = new Outcome();
        service.markDelayed(ride.getId(), ORGANIZER, at(TOMORROW), sameTime);
        sameTime.assertRejectedWith(timeRule);
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
        assertNull(ride.getDelayedDepartureTime());

        Outcome stranger = new Outcome();
        service.markDelayed(ride.getId(), "someone-else", at(TOMORROW + HOUR / 2), stranger);
        stranger.assertRejectedWith("Only the organizer can do this.");
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());

        Outcome delayed = new Outcome();
        service.markDelayed(ride.getId(), ORGANIZER, at(TOMORROW + HOUR / 2), delayed);
        assertNull(delayed.error);
        assertEquals(ActivityStatus.DELAYED, ride.getActivityStatus());
        assertEquals(TOMORROW + HOUR / 2, ride.getDelayedDepartureTime().toDate().getTime());
        assertEquals("The original departure time is kept.", TOMORROW, ride.getDepartureTime().toDate().getTime());
        assertEquals(TOMORROW + HOUR / 2, ride.getCurrentDepartureTime().toDate().getTime());

        // Reverting to On-Time clears the new time; once the original time has passed it is refused.
        Outcome back = new Outcome();
        service.markOnTime(ride.getId(), ORGANIZER, back);
        assertNull(back.error);
        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
        assertNull(ride.getDelayedDepartureTime());
        service.markDelayed(ride.getId(), ORGANIZER, at(TOMORROW + HOUR / 2), new Outcome());
        Outcome afterOriginal = new Outcome();
        new RideService(repository, () -> TOMORROW).markOnTime(ride.getId(), ORGANIZER, afterOriginal);
        afterOriginal.assertRejectedWith("The original departure time has passed.");
        assertEquals(ActivityStatus.DELAYED, ride.getActivityStatus());

        // The other halves of the time condition: no time, and a time that has already passed.
        Ride other = savedRide(NOW + HOUR);
        Outcome noTime = new Outcome();
        service.markDelayed(other.getId(), ORGANIZER, null, noTime);
        noTime.assertRejectedWith(timeRule);
        Outcome past = new Outcome();
        service.markDelayed(other.getId(), ORGANIZER, at(NOW), past);
        past.assertRejectedWith(timeRule);
        assertEquals(ActivityStatus.ON_TIME, other.getActivityStatus());

        ride.setActivityStatus(ActivityStatus.CANCELLED);
        Outcome cancelled = new Outcome();
        service.markDelayed(ride.getId(), ORGANIZER, at(TOMORROW + HOUR), cancelled);
        cancelled.assertRejectedWith("This ride is already cancelled.");
        ride.setActivityStatus(ActivityStatus.COMPLETED);
        Outcome completed = new Outcome();
        service.markDelayed(ride.getId(), ORGANIZER, at(TOMORROW + HOUR), completed);
        completed.assertRejectedWith("This ride is already completed.");
        assertEquals(TOMORROW + HOUR / 2, ride.getDelayedDepartureTime().toDate().getTime());
    }

    /**
     * WB10: the three-hour cancellation cutoff with a fixed clock. Exactly 3 hours before the
     * original departure is allowed; 3 hours minus 1 ms is refused. Non-organizers are refused.
     * The same inputs are run through MembershipTransition.CANCEL_RIDE, which is what the app
     * actually commits, so both implementations of the rule are shown to agree.
     */
    @Test
    public void wb10_cancellationAllowedAtThreeHoursAndRefusedInsideIt() {
        Ride allowed = savedRide(NOW + 3 * HOUR);
        Ride tooLate = savedRide(NOW + 3 * HOUR - 1);

        Outcome refused = new Outcome();
        service.cancelRide(tooLate.getId(), ORGANIZER, refused);
        refused.assertRejectedWith("A ride can't be cancelled within 3 hours of departure.");
        assertEquals(ActivityStatus.ON_TIME, tooLate.getActivityStatus());

        Outcome stranger = new Outcome();
        service.cancelRide(allowed.getId(), "someone-else", stranger);
        stranger.assertRejectedWith("Only the organizer can do this.");
        assertEquals(ActivityStatus.ON_TIME, allowed.getActivityStatus());

        Outcome cancelled = new Outcome();
        service.cancelRide(allowed.getId(), ORGANIZER, cancelled);
        assertNull(cancelled.error);
        assertEquals(ActivityStatus.CANCELLED, allowed.getActivityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, allowed.getCapacityStatus());

        Outcome again = new Outcome();
        service.cancelRide(allowed.getId(), ORGANIZER, again);
        again.assertRejectedWith("This ride is already cancelled.");

        // The committed path: MembershipTransition must draw the line in the same place.
        assertEquals(ActivityStatus.CANCELLED, cancelThroughTransition(savedRide(NOW + 3 * HOUR)).getActivityStatus());
        try {
            cancelThroughTransition(savedRide(NOW + 3 * HOUR - 1));
            fail("MembershipTransition allowed a cancellation inside three hours.");
        } catch (RideRuleException expected) {
            assertEquals("Cancellation requires at least three hours before the original departure.",
                    expected.getMessage());
        }
        // Delaying doesn't move the cutoff: it is measured from the original departure.
        Ride delayed = savedRide(NOW + 3 * HOUR - 1);
        delayed.setActivityStatus(ActivityStatus.DELAYED);
        delayed.setDelayedDepartureTime(at(NOW + 10 * HOUR));
        Outcome delayedCancel = new Outcome();
        service.cancelRide(delayed.getId(), ORGANIZER, delayedCancel);
        delayedCancel.assertRejectedWith("A ride can't be cancelled within 3 hours of departure.");
        assertFalse(delayed.isFinished());
    }

    private static Ride cancelThroughTransition(Ride ride) {
        MembershipTransition.Result result = MembershipTransition.apply(ride, Arrays.<JoinRequest>asList(),
                MembershipTransition.CANCEL_RIDE, ORGANIZER, null, null, null, at(NOW));
        assertTrue(result.rideChanged);
        return result.ride;
    }
}
