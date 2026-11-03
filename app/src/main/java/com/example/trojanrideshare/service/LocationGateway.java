package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.util.Callback;

import java.util.Collections;
import java.util.List;

/**
 * Turns what a user typed into a real place with coordinates (owner: Nikhil).
 * Ride creation (Ada), ride search and history search (Riya) all go through this,
 * so every stored or searched place has the same address + latitude/longitude shape.
 * Distances between places come from {@link Location#distanceMilesTo(Location)}.
 */
public interface LocationGateway {

    /** Reports the best matching place, or an error with a message to show the user if none is found. */
    void lookup(String query, Callback<Location> callback);

    /**
     * Reports up to {@code maxResults} matching places, best first, so the user can pick the right one.
     * Never reports an empty list: no match is an error. Adapters that can only find one place keep this default.
     */
    default void suggest(String query, int maxResults, Callback<List<Location>> callback) {
        lookup(query, new Callback<Location>() {
            @Override
            public void onSuccess(Location result) {
                callback.onSuccess(Collections.singletonList(result));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
}
