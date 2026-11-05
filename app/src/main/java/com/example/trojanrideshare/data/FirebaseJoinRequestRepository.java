package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.List;

public final class FirebaseJoinRequestRepository implements JoinRequestRepository {
    private final FirebaseFirestore db;
    private final MembershipTransactions transactions;
    public FirebaseJoinRequestRepository(FirebaseFirestore db) {
        this.db = db; this.transactions = new MembershipTransactions(db);
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
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) { callback.onError(new SecurityException("Please log in.")); return () -> { }; }
        return observe(db.collection(Constants.COLLECTION_RIDES).where(com.google.firebase.firestore.Filter.or(
                com.google.firebase.firestore.Filter.inArray("activityStatus", java.util.Arrays.asList("ON_TIME", "DELAYED")),
                com.google.firebase.firestore.Filter.equalTo("organizerId", uid),
                com.google.firebase.firestore.Filter.arrayContains("participantIds", uid))), Ride.class, callback);
    }
    @Override public Subscription observeMine(String uid, Callback<List<JoinRequest>> callback) {
        return observe(db.collection(Constants.COLLECTION_JOIN_REQUESTS).whereEqualTo("userId", uid), JoinRequest.class, callback);
    }
    @Override public Subscription observeInbox(String rideId, Callback<List<JoinRequest>> callback) {
        return observe(db.collection(Constants.COLLECTION_JOIN_REQUESTS).whereEqualTo("rideId", rideId), JoinRequest.class, callback);
    }
    @Override public void mutate(String action, String rideId, String requestId, String message, Callback<Void> callback) {
        transactions.run(action, rideId, requestId, message)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
