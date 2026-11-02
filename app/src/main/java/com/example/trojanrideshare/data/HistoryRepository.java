
package com.example.trojanrideshare.data;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        getOrganizedRides(
                uid,
                organizedSnapshot -> {

                    Map<String, DocumentSnapshot> results =
                            new LinkedHashMap<>();

                    // Add rides organized by the user.
                    for (DocumentSnapshot ride :
                            organizedSnapshot.getDocuments()) {

                        results.put(ride.getId(), ride);
                    }

                    // Retrieve the user's join requests.
                    getAcceptedJoinRequests(
                            uid,
                            requestsSnapshot -> {

                                List<Task<DocumentSnapshot>> tasks =
                                        new ArrayList<>();

                                for (DocumentSnapshot request :
                                        requestsSnapshot.getDocuments()) {

                                    String status =
                                            request.getString("status");

                                    String rideId =
                                            request.getString("rideId");

                                    // Only accepted requests count.
                                    if (!"ACCEPTED".equals(status)
                                            || rideId == null
                                            || rideId.trim().isEmpty()) {
                                        continue;
                                    }

                                    // Avoid fetching organized rides again.
                                    if (results.containsKey(rideId)) {
                                        continue;
                                    }

                                    tasks.add(
                                            db.collection("rides")
                                                    .document(rideId)
                                                    .get()
                                    );
                                }

                                // No joined rides to retrieve.
                                if (tasks.isEmpty()) {
                                    onSuccess.onSuccess(
                                            new ArrayList<>(
                                                    results.values()
                                            )
                                    );
                                    return;
                                }

                                Tasks.whenAllSuccess(tasks)
                                        .addOnSuccessListener(documents -> {

                                            for (Object item : documents) {

                                                DocumentSnapshot ride =
                                                        (DocumentSnapshot) item;

                                                if (!ride.exists()) {
                                                    continue;
                                                }

                                                // Verify current membership.
                                                if (isParticipant(ride, uid)) {

                                                    results.put(
                                                            ride.getId(),
                                                            ride
                                                    );
                                                }
                                            }

                                            onSuccess.onSuccess(
                                                    new ArrayList<>(
                                                            results.values()
                                                    )
                                            );
                                        })
                                        .addOnFailureListener(onFailure);
                            },
                            onFailure
                    );
                },
                onFailure
        );
    }

    // Check whether the user is in the ride's participants array.
    private boolean isParticipant(
            DocumentSnapshot ride,
            String uid) {

        Object value = ride.get("participants");

        if (!(value instanceof List)) {
            return false;
        }

        List<?> participants = (List<?>) value;

        for (Object item : participants) {

            if (!(item instanceof Map)) {
                continue;
            }

            Map<?, ?> participant = (Map<?, ?>) item;

            Object userId = participant.get("userId");

            if (uid.equals(userId)) {
                return true;
            }
        }

        return false;
    }
}
