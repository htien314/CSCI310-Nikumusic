package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** In-memory RideRepository for tests. Reports results immediately. */
public class FakeRideRepository implements RideRepository {

    public final Map<String, Ride> rides = new HashMap<>();

    @Override
    public void create(Ride ride, Callback<Ride> callback) {
        ride.setId("ride" + rides.size());
        rides.put(ride.getId(), ride);
        callback.onSuccess(ride);
    }

    @Override
    public void getById(String rideId, Callback<Ride> callback) {
        Ride ride = rides.get(rideId);
        if (ride == null) {
            callback.onError(new NoSuchElementException("Ride not found."));
        } else {
            callback.onSuccess(ride);
        }
    }

    @Override
    public void update(String rideId, RideMutation mutation, Callback<Ride> callback) {
        Ride ride = rides.get(rideId);
        if (ride == null) {
            callback.onError(new NoSuchElementException("Ride not found."));
            return;
        }
        try {
            mutation.apply(ride);
            callback.onSuccess(ride);
        } catch (RuntimeException e) {
            callback.onError(e);
        }
    }
}
