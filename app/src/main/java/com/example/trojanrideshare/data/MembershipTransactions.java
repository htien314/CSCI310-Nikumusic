package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.MembershipTransition;
import com.example.trojanrideshare.service.RideRuleException;
import com.example.trojanrideshare.util.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs membership actions as Firestore transactions from the app, replacing the team's
 * {@code membership} Cloud Function so the project works on the free Spark plan.
 * MembershipTransition decides the outcome; firestore.rules re-check permissions and cutoffs.
 */
final class MembershipTransactions {

    private static final List<String> CLOSES_PENDING_REQUESTS = Arrays.asList(
            MembershipTransition.ACCEPT, MembershipTransition.LEAVE, MembershipTransition.CANCEL_RIDE);

    private final FirebaseFirestore db;

    MembershipTransactions(FirebaseFirestore db) {
        this.db = db;
    }

    Task<Void> run(String action, String rideId, String targetId, String message) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return Tasks.forException(new SecurityException("Please log in first."));
        }
        CollectionReference requests = db.collection(Constants.COLLECTION_JOIN_REQUESTS);

        // Transactions can't run queries, so the pending requests are found first and re-read inside.
        // Only the organizer may list a ride's requests; for anyone else the list is simply empty.
        Task<List<DocumentReference>> pending = CLOSES_PENDING_REQUESTS.contains(action)
                ? requests.whereEqualTo("rideId", rideId).whereEqualTo("status", "PENDING").get()
                        .continueWith(task -> task.isSuccessful() ? refs(task.getResult())
                                : Collections.<DocumentReference>emptyList())
                : Tasks.forResult(Collections.emptyList());

        return pending
                .continueWithTask(task -> db.runTransaction(transaction -> {
                    apply(transaction, action, rideId, targetId, message, uid, task.getResult());
                    return null;
                }))
                .continueWithTask(task -> task.isSuccessful()
                        ? Tasks.forResult((Void) null)
                        : Tasks.forException(forUser(task.getException())));
    }

    private void apply(Transaction transaction, String action, String rideId, String targetId,
                       String message, String uid, List<DocumentReference> pendingRefs)
            throws FirebaseFirestoreException {
        DocumentReference rideRef = db.collection(Constants.COLLECTION_RIDES).document(rideId);
        CollectionReference requests = db.collection(Constants.COLLECTION_JOIN_REQUESTS);

        DocumentSnapshot rideSnapshot = transaction.get(rideRef);
        if (!rideSnapshot.exists()) throw new RideRuleException("Ride not found.");
        Ride ride = rideSnapshot.toObject(Ride.class);

        Map<String, JoinRequest> found = new LinkedHashMap<>();
        addRequest(found, transaction.get(requests.document(MembershipTransition.requestId(rideId, uid))), rideId);
        if (targetId != null) addRequest(found, transaction.get(requests.document(targetId)), rideId);
        for (DocumentReference ref : pendingRefs) addRequest(found, transaction.get(ref), rideId);

        String displayName = null;
        if (MembershipTransition.REQUEST.equals(action)) {
            DocumentSnapshot profile = transaction.get(db.collection("users").document(uid));
            displayName = profile.getString("nickname");
            if (displayName == null) throw new RideRuleException("Finish your profile before requesting rides.");
        }

        MembershipTransition.Result result = MembershipTransition.apply(ride, new ArrayList<>(found.values()),
                action, uid, targetId, message, displayName, Timestamp.now());

        if (result.rideChanged) {
            // Only membership fields; route, times, notes and costs are never rewritten.
            Map<String, Object> fields = new HashMap<>();
            fields.put("participants", participantMaps(result.ride.getParticipants()));
            fields.put("participantIds", result.ride.getParticipantIds());
            fields.put("organizerId", result.ride.getOrganizerId());
            fields.put("activityStatus", result.ride.getActivityStatus().name());
            fields.put("capacityStatus", result.ride.getCapacityStatus().name());
            transaction.update(rideRef, fields);
        }
        for (JoinRequest request : result.changedRequests) {
            transaction.set(requests.document(request.getId()), requestMap(request, found.containsKey(request.getId())));
        }
    }

    private static void addRequest(Map<String, JoinRequest> found, DocumentSnapshot snapshot, String rideId) {
        if (!snapshot.exists()) return;
        JoinRequest request = snapshot.toObject(JoinRequest.class);
        if (request != null && rideId.equals(request.getRideId())) found.put(snapshot.getId(), request);
    }

    private static List<DocumentReference> refs(QuerySnapshot snapshot) {
        List<DocumentReference> refs = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) refs.add(document.getReference());
        return refs;
    }

    private static List<Map<String, Object>> participantMaps(List<Participant> participants) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Participant participant : participants) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", participant.getUserId());
            map.put("displayName", participant.getDisplayName());
            map.put("acceptedAt", participant.getAcceptedAt());
            maps.add(map);
        }
        return maps;
    }

    // The server stamps updatedAt (and createdAt for a new or resubmitted request), so the rules can check it.
    private static Map<String, Object> requestMap(JoinRequest request, boolean existed) {
        boolean submitted = request.getStatus() == JoinRequest.Status.PENDING;
        Map<String, Object> map = new HashMap<>();
        map.put("rideId", request.getRideId());
        map.put("userId", request.getUserId());
        map.put("displayName", request.getDisplayName());
        map.put("message", request.getMessage() == null ? "" : request.getMessage());
        map.put("status", request.getStatus().name());
        map.put("createdAt", submitted || !existed ? FieldValue.serverTimestamp() : request.getCreatedAt());
        map.put("updatedAt", FieldValue.serverTimestamp());
        if (!submitted && request.getClosedReason() != null) map.put("closedReason", request.getClosedReason());
        return map;
    }

    /** Rule messages pass through; a rules denial means the ride changed or a cutoff passed meanwhile. */
    static Exception forUser(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RideRuleException || cause instanceof SecurityException) return (Exception) cause;
            cause = cause.getCause();
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return new RideRuleException("This change isn't allowed right now. Refresh the ride and try again.");
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return new RideRuleException("Couldn't reach the server. Check your connection and try again.");
            }
        }
        return e;
    }
}
