package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.NoSuchElementException;

/** RideRepository backed by the Firestore {@code rides} collection. */
public class FirestoreRideRepository implements RideRepository {

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

}
