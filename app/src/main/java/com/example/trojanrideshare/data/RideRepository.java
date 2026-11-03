package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;

/** Stores and loads rides. Business rules belong in RideService, not here. */
public interface RideRepository {

    /** A change to one ride. Throw to cancel it. It may run more than once, so only change the ride. */
    interface RideMutation {
        void apply(Ride ride);
    }

    /** Saves a new ride and reports it back with its generated ID. */
    void create(Ride ride, Callback<Ride> callback);

    void getById(String rideId, Callback<Ride> callback);

    /** Loads the latest ride, applies the change and saves it as one atomic update. */
    void update(String rideId, RideMutation mutation, Callback<Ride> callback);
}
