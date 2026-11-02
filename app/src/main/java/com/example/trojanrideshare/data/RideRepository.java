package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;

import java.util.List;

/** Stores and loads rides. Business rules belong in RideService, not here. */
public interface RideRepository {

    /** A change to one ride. Throw to cancel it. It may run more than once, so only change the ride. */
    interface RideMutation {
        void apply(Ride ride);
    }

    /** Stops a live listener. Safe to call more than once. */
    interface Subscription {
        void cancel();
    }

    /** Saves a new ride and reports it back with its generated ID. */
    void create(Ride ride, Callback<Ride> callback);

    void getById(String rideId, Callback<Ride> callback);

    /** Loads the latest ride, applies the change and saves it as one atomic update. */
    void update(String rideId, RideMutation mutation, Callback<Ride> callback);

    /**
     * Loads every ride that is On-Time or Delayed, for ride search (owner: Nikhil).
     * Errors: {@link SecurityException} if the database rules deny the read, {@link java.io.IOException}
     * if the database can't be reached.
     */
    void findActive(Callback<List<Ride>> callback);

    /**
     * Like {@link #findActive}, but reports the list again whenever any active ride changes, so search
     * results stay current while other users join, leave, delay or cancel rides (owner: Nikhil).
     */
    Subscription listenToActive(Callback<List<Ride>> callback);
}
