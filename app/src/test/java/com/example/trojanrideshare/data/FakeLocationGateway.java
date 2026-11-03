package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.util.Callback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/** Answers from a fixed list of places, so tests don't need the device's location service. */
public class FakeLocationGateway implements LocationGateway {

    private static final List<Location> PLACES = Arrays.asList(
            new Location("USC Village", 34.0256, -118.2850),
            new Location("LAX", 33.9416, -118.4085),
            new Location("Union Station", 34.0562, -118.2365),
            new Location("Santa Monica Pier", 34.0094, -118.4973),
            new Location("Hollywood Bowl", 34.1128, -118.3392));

    @Override
    public void lookup(String query, Callback<Location> callback) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (search.isEmpty()) {
            callback.onError(new IllegalArgumentException("Enter an origin and a destination."));
            return;
        }
        for (Location place : PLACES) {
            if (place.getAddress().toLowerCase(Locale.US).contains(search)) {
                callback.onSuccess(place);
                return;
            }
        }
        callback.onError(new NoSuchElementException("Couldn't find \"" + query.trim() + "\"."));
    }
}
