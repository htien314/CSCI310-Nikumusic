
package com.example.trojanrideshare.data;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

public class HistoryRepository {

    private final FirebaseFirestore db;

    public HistoryRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // Retrieve rides organized by the current user.
    public void getOrganizedRides(
            String uid,
            OnSuccessListener<QuerySnapshot> onSuccess,
            OnFailureListener onFailure) {

        db.collection("rides")
                .whereEqualTo("organizerId", uid)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Retrieve join requests belonging to the current user.
    public void getAcceptedJoinRequests(
            String uid,
            OnSuccessListener<QuerySnapshot> onSuccess,
            OnFailureListener onFailure) {

        db.collection("joinRequests")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Retrieve rides organized by or currently joined by the user.
    public void getMyRides(
            String uid,
            OnSuccessListener<List<DocumentSnapshot>> onSuccess,
            OnFailureListener onFailure) {

        if (uid == null || uid.trim().isEmpty()) {
            onFailure.onFailure(
                    new IllegalArgumentException("Please log in.")
            );
            return;
        }

        db.collection("rides").where(com.google.firebase.firestore.Filter.or(
                com.google.firebase.firestore.Filter.equalTo("organizerId", uid),
                com.google.firebase.firestore.Filter.arrayContains("participantIds", uid)))
                .get().addOnSuccessListener(snapshot -> onSuccess.onSuccess(snapshot.getDocuments()))
                .addOnFailureListener(onFailure);
    }
}
