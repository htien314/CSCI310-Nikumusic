package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.JoinRequestRepository;
import com.example.trojanrideshare.util.Callback;

/** UI entry point. Authoritative state/authorization/time checks run in the server transaction. */
public final class MembershipService {
    private final JoinRequestRepository repository;
    public MembershipService(JoinRequestRepository repository) { this.repository = repository; }
    public void request(String rideId, String message, Callback<Void> callback) {
        if (message != null && message.length() > 1000) {
            callback.onError(new IllegalArgumentException("Use a message of at most 1,000 characters.")); return;
        }
        act("request", rideId, null, message, callback);
    }
    public void cancel(String rideId, Callback<Void> callback) { act("cancel", rideId, null, null, callback); }
    public void accept(String rideId, String requestId, Callback<Void> callback) { act("accept", rideId, requestId, null, callback); }
    public void reject(String rideId, String requestId, Callback<Void> callback) { act("reject", rideId, requestId, null, callback); }
    public void leave(String rideId, Callback<Void> callback) { act("leave", rideId, null, null, callback); }
    private void act(String action, String rideId, String requestId, String message, Callback<Void> callback) {
        if (rideId == null || rideId.trim().isEmpty() || rideId.contains("/")) {
            callback.onError(new IllegalArgumentException("Choose a ride first.")); return;
        }
        if ((action.equals("accept") || action.equals("reject")) && (requestId == null || requestId.isEmpty())) {
            callback.onError(new IllegalArgumentException("Choose a request first.")); return;
        }
        repository.mutate(action, rideId, requestId, message, callback);
    }
}
