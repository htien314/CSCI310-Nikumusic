package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FirebaseJoinRequestRepository implements JoinRequestRepository {
    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;
    public FirebaseJoinRequestRepository(FirebaseFirestore db, FirebaseFunctions functions) {
        this.db = db; this.functions = functions;
    }
    private <T> Subscription observe(Query query, Class<T> type, Callback<List<T>> callback) {
        return query.addSnapshotListener((snapshot, error) -> {
            if (error != null) { callback.onError(error); return; }
            if (snapshot == null) return;
            try { callback.onSuccess(snapshot.toObjects(type)); }
            catch (RuntimeException e) { callback.onError(e); }
        })::remove;
    }
    @Override public Subscription observeRides(Callback<List<Ride>> callback) {
        return observe(db.collection(Constants.COLLECTION_RIDES), Ride.class, callback);
    }
    @Override public Subscription observeMine(String uid, Callback<List<JoinRequest>> callback) {
        return observe(db.collection(Constants.COLLECTION_JOIN_REQUESTS).whereEqualTo("userId", uid), JoinRequest.class, callback);
    }
    @Override public Subscription observeInbox(String rideId, Callback<List<JoinRequest>> callback) {
        return observe(db.collection(Constants.COLLECTION_JOIN_REQUESTS).whereEqualTo("rideId", rideId), JoinRequest.class, callback);
    }
    @Override public void mutate(String action, String rideId, String requestId, String message, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action); data.put("rideId", rideId);
        if (requestId != null) data.put("targetId", requestId);
        if (message != null) data.put("message", message);
        functions.getHttpsCallable("membership").call(data)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
