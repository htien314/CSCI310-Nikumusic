package com.example.trojanrideshare.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.trojanrideshare.model.Location;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/** Calls the real OpenStreetMap service from the device, so it needs network access. */
@RunWith(AndroidJUnit4.class)
public class NominatimLocationGatewayDeviceTest {

    @Test
    public void findsUscVillageAndLax() throws Exception {
        NominatimLocationGateway gateway = new NominatimLocationGateway();

        List<Location> usc = gateway.search("USC Village", 1);
        Thread.sleep(1100); // Nominatim allows one request per second.
        List<Location> lax = gateway.search("LAX", 1);

        assertEquals(34.026, usc.get(0).getLatitude(), 0.01);
        assertEquals(-118.285, usc.get(0).getLongitude(), 0.01);
        assertEquals(33.942, lax.get(0).getLatitude(), 0.02);
        assertEquals(-118.41, lax.get(0).getLongitude(), 0.02);
    }
}
