package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.util.Callback;

/** Turns what a user typed into a real place with coordinates (owner: Nikhil). */
public interface LocationGateway {

    /** Reports the matching place, or an error with a message to show the user if none is found. */
    void lookup(String query, Callback<Location> callback);
}
