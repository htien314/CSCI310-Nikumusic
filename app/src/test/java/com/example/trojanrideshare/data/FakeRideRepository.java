package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /** Set this to make findActive fail, like a lost connection. */
    public Exception findActiveError;

    @Override
    public void findActive(Callback<List<Ride>> callback) {
        if (findActiveError != null) {
            callback.onError(findActiveError);
            return;
        }
        List<Ride> active = new ArrayList<>();
        for (Ride ride : rides.values()) {
            if (ride.getActivityStatus() == ActivityStatus.ON_TIME
                    || ride.getActivityStatus() == ActivityStatus.DELAYED) {
                active.add(ride);
            }
        }
        callback.onSuccess(active);
    }

    /** Live listeners that haven't been cancelled. */
    public final List<Callback<List<Ride>>> listeners = new ArrayList<>();

    @Override
    public Subscription listenToActive(Callback<List<Ride>> callback) {
        listeners.add(callback);
        findActive(callback);
        return () -> listeners.remove(callback);
    }

    /** Call after changing rides in a test to push the update to live listeners, like Firestore would. */
    public void notifyListeners() {
        for (Callback<List<Ride>> listener : new ArrayList<>(listeners)) {
            findActive(listener);
        }
    }
}
