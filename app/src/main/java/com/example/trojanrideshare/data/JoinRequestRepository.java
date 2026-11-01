package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import java.util.List;

/** Reads stream from Firestore; writes run through the trusted membership callable. */
public interface JoinRequestRepository {
    interface Subscription { void remove(); }
    Subscription observeRides(Callback<List<Ride>> callback);
    Subscription observeMine(String userId, Callback<List<JoinRequest>> callback);
    Subscription observeInbox(String rideId, Callback<List<JoinRequest>> callback);
    void mutate(String action, String rideId, String requestId, String message, Callback<Void> callback);
}
