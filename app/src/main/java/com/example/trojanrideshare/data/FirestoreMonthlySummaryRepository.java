package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class FirestoreMonthlySummaryRepository {

    private final FirebaseFirestore db;

    public FirestoreMonthlySummaryRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // Load rides and keep only those containing this user.
    public void getRidesForUser(
            String userId,
            Callback<List<Ride>> callback) {

        if (userId == null || userId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException(
                    "A user ID is required."));
            return;
        }

        db.collection(Constants.COLLECTION_RIDES)
                .whereArrayContains("participantIds", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Ride> rides = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshot.getDocuments()) {

                        Ride ride = doc.toObject(Ride.class);

                        if (ride != null
                                && ride.getCostShares() != null
                                && ride.getCostShares().containsKey(userId)) {
                            rides.add(ride);
                        }
                    }

                    callback.onSuccess(rides);
                })
                .addOnFailureListener(callback::onError);
    }
}
