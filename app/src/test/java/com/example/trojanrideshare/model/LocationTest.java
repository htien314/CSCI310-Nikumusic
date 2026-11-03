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
    public void isValid_rejectsOutOfRangeCoordinates() {
        assertFalse(new Location("LAX", 90.1, -118.408).isValid());
        assertFalse(new Location("LAX", 33.942, -180.1).isValid());
    }
}
