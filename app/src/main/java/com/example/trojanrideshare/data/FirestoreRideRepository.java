package com.example.trojanrideshare.data;

import android.util.Log;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/** RideRepository backed by the Firestore {@code rides} collection. */
public class FirestoreRideRepository implements RideRepository {

    private static final String TAG = "FirestoreRideRepository";

    private final FirebaseFirestore db;
    private final CollectionReference rides;

    public FirestoreRideRepository(FirebaseFirestore db) {
        this.db = db;
        this.rides = db.collection(Constants.COLLECTION_RIDES);
    }

    @Override
    public void create(Ride ride, Callback<Ride> callback) {
        DocumentReference ref = rides.document();
        ride.syncParticipantIds();
        ref.set(ride)
                .addOnSuccessListener(unused -> {
                    ride.setId(ref.getId());
                    callback.onSuccess(ride);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getById(String rideId, Callback<Ride> callback) {
        rides.document(rideId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(snapshot.toObject(Ride.class));
                    } else {
                        callback.onError(new NoSuchElementException("Ride not found."));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void update(String rideId, RideMutation mutation, Callback<Ride> callback) {
        DocumentReference ref = rides.document(rideId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(ref);
                    if (!snapshot.exists()) {
                        throw new NoSuchElementException("Ride not found.");
                    }
                    Ride ride = snapshot.toObject(Ride.class);
                    mutation.apply(ride);

                    // Keep participant IDs synchronized before saving.
                    ride.syncParticipantIds();

                    transaction.set(ref, ride);
                    return ride;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void findActive(Callback<List<Ride>> callback) {
        activeRides().get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(toRides(snapshot)))
                .addOnFailureListener(e -> callback.onError(translate(e)));
    }

    @Override
    public Subscription listenToActive(Callback<List<Ride>> callback) {
        ListenerRegistration registration = activeRides().addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                callback.onError(translate(e));
            } else if (snapshot != null) {
                callback.onSuccess(toRides(snapshot));
            }
        });
        return registration::remove;
    }

    /** Enums are stored by name. A whereIn on one field needs no composite index. */
    private Query activeRides() {
        return rides.whereIn("activityStatus",
                Arrays.asList(ActivityStatus.ON_TIME.name(), ActivityStatus.DELAYED.name()));
    }

    /** Skips documents that can't be read as a Ride, so one bad document doesn't break search. */
    private static List<Ride> toRides(QuerySnapshot snapshot) {
        List<Ride> result = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            try {
                Ride ride = document.toObject(Ride.class);
                if (ride != null) {
                    result.add(ride);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Skipping unreadable ride " + document.getId(), e);
            }
        }
        return result;
    }

    /** Turns Firestore errors into the types RideRepository documents, so services don't depend on Firestore. */
    private static Exception translate(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return new SecurityException(e.getMessage(), e);
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return new IOException(e.getMessage(), e);
            }
        }
        return e;
    }
}
