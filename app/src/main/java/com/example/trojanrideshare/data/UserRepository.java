
package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private final FirebaseFirestore db;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // Save a student profile.
    public void saveUser(
            User user,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure) {

        db.collection("users")
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Retrieve a student profile by Firebase UID.
    public void getUser(
            String uid,
            OnSuccessListener<DocumentSnapshot> onSuccess,
            OnFailureListener onFailure) {

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Update the student's nickname.
    public void updateNickname(
            String uid,
            String nickname,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure) {

        db.collection("users")
                .document(uid)
                .update("nickname", nickname)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Register a student profile and reserve their USC ID.
    // Both Firestore writes happen in one transaction.
    public void registerUser(
            User user,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure) {

        DocumentReference userRef =
                db.collection("users")
                        .document(user.getUid());

        DocumentReference idRef =
                db.collection("uscIdReservations")
                        .document(user.getUscId());

        db.runTransaction(transaction -> {

            // Check whether the USC ID is already registered.
            DocumentSnapshot existingId =
                    transaction.get(idRef);

            // Check whether the user profile already exists.
            DocumentSnapshot existingUser =
                    transaction.get(userRef);

            if (existingId.exists()) {
                throw new IllegalStateException(
                        "This USC ID is already registered."
                );
            }

            if (existingUser.exists()) {
                throw new IllegalStateException(
                        "A profile already exists for this account."
                );
            }

            // Reserve the USC ID for this Firebase user.
            Map<String, Object> reservation =
                    new HashMap<>();

            reservation.put("uid", user.getUid());

            // Save both records atomically.
            transaction.set(idRef, reservation);
            transaction.set(userRef, user);

            return null;

        }).addOnSuccessListener(result -> {
            onSuccess.onSuccess(null);
        }).addOnFailureListener(onFailure);
    }
}
