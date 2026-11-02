package com.example.trojanrideshare.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocationTest {

    @Test
    public void isValid_acceptsAddressWithRealCoordinates() {
        assertTrue(new Location("LAX", 33.942, -118.408).isValid());
        assertTrue(new Location("Corner", 90, -180).isValid());
    }

    @Test
    public void isValid_rejectsMissingAddress() {
        assertFalse(new Location(null, 33.942, -118.408).isValid());
        assertFalse(new Location("   ", 33.942, -118.408).isValid());
    }

    @Test
    public void distanceMilesTo_samePlaceIsZero() {
        Location lax = new Location("LAX", 33.9416, -118.4085);
        assertEquals(0.0, lax.distanceMilesTo(lax), 1e-9);
    }

    @Test
    public void distanceMilesTo_uscToLaxIsAboutNineMiles() {
        Location usc = new Location("USC Village", 34.0256, -118.2850);
        Location lax = new Location("LAX", 33.9416, -118.4085);
        assertEquals(9.3, usc.distanceMilesTo(lax), 0.2);
        assertEquals(usc.distanceMilesTo(lax), lax.distanceMilesTo(usc), 1e-9);
    }

    @Test
    public void distanceMilesTo_longDistance() {
        Location la = new Location("Los Angeles", 34.0522, -118.2437);
        Location ny = new Location("New York", 40.7128, -74.0060);
        assertEquals(2445, la.distanceMilesTo(ny), 10);
    }

    @Test
    public void isValid_rejectsOutOfRangeCoordinates() {
        assertFalse(new Location("LAX", 90.1, -118.408).isValid());
        assertFalse(new Location("LAX", 33.942, -180.1).isValid());
    }
}
