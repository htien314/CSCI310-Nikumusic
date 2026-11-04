package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.CostGateway;
import com.example.trojanrideshare.service.CostTransition;
import com.example.trojanrideshare.service.RideRuleException;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completion and payment updates as app-side Firestore transactions, replacing the team's
 * {@code rideCosts} Cloud Function so the project works on the free Spark plan.
 * CostTransition decides the outcome; firestore.rules re-check permissions and cutoffs.
 */
public final class FirebaseCostGateway implements CostGateway {
    private final RideRepository rides;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public FirebaseCostGateway(RideRepository rides) { this.rides = rides; }

    @Override public void execute(String action, String rideId, long totalCents,
            Map<String, Long> shares, String riderId, Callback<Ride> callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { callback.onError(new SecurityException("Please log in first.")); return; }

        // Completion closes pending requests; transactions can't query, so find them first.
        Task<List<DocumentReference>> pending = CostTransition.COMPLETE.equals(action)
                ? db.collection(Constants.COLLECTION_JOIN_REQUESTS).whereEqualTo("rideId", rideId)
                        .whereEqualTo("status", "PENDING").get()
                        .continueWith(task -> {
                            List<DocumentReference> refs = new ArrayList<>();
                            if (task.isSuccessful()) {
                                for (DocumentSnapshot doc : task.getResult().getDocuments()) refs.add(doc.getReference());
                            }
                            return refs;
                        })
                : Tasks.forResult(Collections.emptyList());

        pending.continueWithTask(task -> db.runTransaction(transaction -> {
                    apply(transaction, action, rideId, totalCents, shares, riderId, uid, task.getResult());
                    return null;
                }))
                .addOnSuccessListener(unused -> rides.getById(rideId, callback))
                .addOnFailureListener(e -> callback.onError(MembershipTransactions.forUser(e)));
    }

    private void apply(Transaction transaction, String action, String rideId, long totalCents,
                       Map<String, Long> customShares, String riderId, String uid,
                       List<DocumentReference> pendingRefs) throws com.google.firebase.firestore.FirebaseFirestoreException {
        DocumentReference rideRef = db.collection(Constants.COLLECTION_RIDES).document(rideId);
        DocumentSnapshot snapshot = transaction.get(rideRef);
        if (!snapshot.exists()) throw new RideRuleException("Ride not found.");
        Ride ride = snapshot.toObject(Ride.class);
        Timestamp now = Timestamp.now();

        if (CostTransition.COMPLETE.equals(action)) {
            List<DocumentSnapshot> stillPending = new ArrayList<>();
            for (DocumentReference ref : pendingRefs) {
                DocumentSnapshot request = transaction.get(ref);
                if (request.exists() && "PENDING".equals(request.getString("status"))) stillPending.add(request);
            }
            Map<String, CostShare> result = CostTransition.complete(ride, uid, totalCents, customShares, now);
            List<String> ids = new ArrayList<>(result.keySet());

            Map<String, Object> fields = new HashMap<>();
            fields.put("activityStatus", "COMPLETED");
            fields.put("capacityStatus", "NOT_APPLICABLE");
            fields.put("actualCostCents", totalCents);
            // Server time, so the rules can check the ride really departed.
            fields.put("completedAt", FieldValue.serverTimestamp());
            fields.put("participantIds", ids);
            fields.put("costShares", shareMaps(result));
            transaction.update(rideRef, fields);
            for (DocumentSnapshot request : stillPending) {
                Map<String, Object> closed = new HashMap<>();
                closed.put("status", "REJECTED");
                closed.put("closedReason", "RIDE_COMPLETED");
                closed.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(request.getReference(), closed);
            }
            return;
        }

        DocumentReference publicRef = db.collection("publicProfiles").document(riderId == null ? "-" : riderId);
        DocumentSnapshot profile = transaction.get(publicRef);
        Map<String, CostShare> result = CostTransition.updateShare(ride, action, uid, riderId, now);

        // The rider's public profile lists rides with flagged, unpaid shares.
        List<String> overdue = new ArrayList<>();
        Object existing = profile.get("overdueRideIds");
        if (existing instanceof List) {
            for (Object id : (List<?>) existing) if (id instanceof String && !id.equals(rideId)) overdue.add((String) id);
        }
        if (result.get(riderId).isFlagged()) overdue.add(rideId);

        transaction.update(rideRef, "costShares", shareMaps(result));
        boolean listChanged = !(existing instanceof List) ? !overdue.isEmpty() : !overdue.equals(existing);
        if (profile.exists() && listChanged) {
            transaction.set(publicRef, Collections.singletonMap("overdueRideIds", overdue), SetOptions.merge());
        }
    }

    private static Map<String, Object> shareMaps(Map<String, CostShare> shares) {
        Map<String, Object> maps = new LinkedHashMap<>();
        for (Map.Entry<String, CostShare> entry : shares.entrySet()) {
            Map<String, Object> share = new HashMap<>();
            share.put("riderId", entry.getValue().getRiderId());
            share.put("amountCents", entry.getValue().getAmountCents());
            share.put("paid", entry.getValue().isPaid());
            share.put("flagged", entry.getValue().isFlagged());
            maps.put(entry.getKey(), share);
        }
        return maps;
    }
}
