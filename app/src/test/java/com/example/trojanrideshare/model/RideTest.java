package com.example.trojanrideshare.model;

import com.google.firebase.Timestamp;

import org.junit.Test;

import static org.junit.Assert.*;

public class RideTest {

    private static final Timestamp CREATED = new Timestamp(1_000, 0);
    private static final Timestamp DEPARTURE = new Timestamp(2_000, 0);
    private static final Timestamp DELAYED = new Timestamp(3_000, 0);

    private static Ride newRide(int maxRiders) {
        Location usc = new Location("USC Village", 34.025, -118.285);
        Location lax = new Location("LAX", 33.942, -118.408);
        return Ride.createNew("To LAX", usc, lax, DEPARTURE, maxRiders, 6_000, "Note",
                "organizer", "Ada", CREATED);
    }

    private static void addRider(Ride ride, String userId) {
        ride.getParticipants().add(new Participant(userId, userId, CREATED));
        ride.syncCapacityStatus();
    }

    @Test
    public void getOrganizerName_findsOrganizerAmongParticipants() {
        Ride ride = newRide(4);
        addRider(ride, "rider");

        assertEquals("Ada", ride.getOrganizerName());
    }

    @Test
    public void isFinished_onlyForCancelledAndCompleted() {
        Ride ride = newRide(4);
        assertFalse(ride.isFinished());

        ride.setActivityStatus(ActivityStatus.DELAYED);
        assertFalse(ride.isFinished());

        ride.setActivityStatus(ActivityStatus.CANCELLED);
        assertTrue(ride.isFinished());

        ride.setActivityStatus(ActivityStatus.COMPLETED);
        assertTrue(ride.isFinished());
    }

    @Test
    public void createNew_startsOnTimeAndOpen() {
        Ride ride = newRide(4);

        assertEquals(ActivityStatus.ON_TIME, ride.getActivityStatus());
        assertEquals(CapacityStatus.OPEN, ride.getCapacityStatus());
    }

    @Test
    public void createNew_makesOrganizerTheFirstParticipant() {
        Ride ride = newRide(4);

        assertEquals(1, ride.getParticipants().size());
        assertEquals("organizer", ride.getParticipants().get(0).getUserId());
        assertEquals("Ada", ride.getParticipants().get(0).getDisplayName());
        assertEquals(CREATED, ride.getParticipants().get(0).getAcceptedAt());
        assertEquals(3, ride.getAvailableSeats());
    }

    @Test
    public void isOrganizer_matchesOnlyTheOrganizer() {
        Ride ride = newRide(4);

        assertTrue(ride.isOrganizer("organizer"));
        assertFalse(ride.isOrganizer("rider"));
        assertFalse(ride.isOrganizer(null));
    }

    @Test
    public void capacity_becomesFullAtMaxRiders() {
        Ride ride = newRide(2);

        addRider(ride, "rider");

        assertEquals(CapacityStatus.FULL, ride.getCapacityStatus());
        assertEquals(0, ride.getAvailableSeats());
    }

    @Test
    public void capacity_reopensWhenARiderLeaves() {
        Ride ride = newRide(2);
        addRider(ride, "rider");

        ride.getParticipants().remove(1);
        ride.syncCapacityStatus();

        assertEquals(CapacityStatus.OPEN, ride.getCapacityStatus());
        assertEquals(1, ride.getAvailableSeats());
    }

    @Test
    public void capacity_isNotApplicableWhenCancelledOrCompleted() {
        Ride cancelled = newRide(4);
        cancelled.setActivityStatus(ActivityStatus.CANCELLED);
        cancelled.syncCapacityStatus();

        Ride completed = newRide(2);
        addRider(completed, "rider");
        completed.setActivityStatus(ActivityStatus.COMPLETED);
        completed.syncCapacityStatus();

        assertEquals(CapacityStatus.NOT_APPLICABLE, cancelled.getCapacityStatus());
        assertEquals(CapacityStatus.NOT_APPLICABLE, completed.getCapacityStatus());
    }

    @Test
    public void currentDepartureTime_usesDelayedTimeWhenSet() {
        Ride ride = newRide(4);
        assertEquals(DEPARTURE, ride.getCurrentDepartureTime());

        ride.setDelayedDepartureTime(DELAYED);
        assertEquals(DELAYED, ride.getCurrentDepartureTime());
        assertEquals(DEPARTURE, ride.getDepartureTime());

        ride.setActivityStatus(ActivityStatus.COMPLETED);
        assertEquals(DELAYED, ride.getCurrentDepartureTime());
    }
}
