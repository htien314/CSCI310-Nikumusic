package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Location;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class NominatimLocationGatewayTest {

    @Test
    public void parse_readsPlacesAndShortensAddresses() throws IOException {
        String json = "[{\"display_name\":\"USC Village, South Hoover Street, University Park, Los Angeles,"
                + " Los Angeles County, California, 90089, United States\",\"lat\":\"34.0258\",\"lon\":\"-118.2850\"},"
                + "{\"display_name\":\"Los Angeles International Airport, 1, World Way, Westchester\","
                + "\"lat\":\"33.9421\",\"lon\":\"-118.4210\"}]";

        List<Location> places = NominatimLocationGateway.parse(json);

        assertEquals(2, places.size());
        assertEquals("USC Village, South Hoover Street, University Park, Los Angeles, California, 90089",
                places.get(0).getAddress());
        assertEquals(34.0258, places.get(0).getLatitude(), 1e-9);
        assertEquals(-118.421, places.get(1).getLongitude(), 1e-9);
    }

    @Test
    public void parse_emptyResultMeansNotFound() throws IOException {
        assertTrue(NominatimLocationGateway.parse("[]").isEmpty());
    }

    @Test
    public void parse_skipsEntriesWithoutCoordinates() throws IOException {
        String json = "[{\"display_name\":\"Somewhere\"},"
                + "{\"display_name\":\"Elsewhere\",\"lat\":\"95\",\"lon\":\"0\"},"
                + "{\"display_name\":\"LAX\",\"lat\":\"33.94\",\"lon\":\"-118.41\"}]";

        List<Location> places = NominatimLocationGateway.parse(json);

        assertEquals(1, places.size());
        assertEquals("LAX", places.get(0).getAddress());
    }

    @Test(expected = IOException.class)
    public void parse_rejectsUnexpectedResponse() throws IOException {
        NominatimLocationGateway.parse("{\"error\":\"Too many requests\"}");
    }
}
