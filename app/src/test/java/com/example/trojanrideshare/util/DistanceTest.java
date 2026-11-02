package com.example.trojanrideshare.util;

import com.example.trojanrideshare.model.Location;

import org.junit.Test;

import static org.junit.Assert.*;

public class DistanceTest {

    private static final Location USC = new Location("USC Village", 34.0256, -118.2850);
    private static final Location LAX = new Location("LAX", 33.9416, -118.4085);

    @Test
    public void withinRadius_boundaryCountsAsInside() {
        double exact = USC.distanceMilesTo(LAX);
        assertTrue(Distance.withinRadius(USC, LAX, exact));
        assertFalse(Distance.withinRadius(USC, LAX, exact - 0.001));
    }

    @Test
    public void withinRadius_missingOrInvalidPlaceNeverMatches() {
        assertFalse(Distance.withinRadius(null, LAX, 100));
        assertFalse(Distance.withinRadius(USC, null, 100));
        assertFalse(Distance.withinRadius(new Location("Bad", 91, 0), LAX, 10_000));
    }

    @Test
    public void format_oneDecimalWithUnit() {
        assertEquals("0.4 mi", Distance.format(0.36));
        assertEquals("12.0 mi", Distance.format(12));
    }
}
