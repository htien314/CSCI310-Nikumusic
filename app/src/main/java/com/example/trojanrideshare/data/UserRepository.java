
package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.User;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private final FirebaseFirestore db;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // Legacy helper for saving a private user profile.
    // Use registerUser() for new student registrations.
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

    // Retrieve a student's private profile.
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

    // Update the nickname in both private and public profiles.
    public void updateNickname(
            String uid,
            String nickname,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure) {

        if (nickname == null || nickname.trim().isEmpty()) {
            onFailure.onFailure(
                    new IllegalArgumentException(
                            "Nickname cannot be empty."
                    )
            );
            return;
        }

        String cleanNickname = nickname.trim();

        DocumentReference userRef =
                db.collection("users")
                        .document(uid);

        DocumentReference publicRef =
                db.collection("publicProfiles")
                        .document(uid);

        db.runTransaction(transaction -> {

            // Read both profiles before performing updates.
            DocumentSnapshot privateProfile =
                    transaction.get(userRef);

            DocumentSnapshot publicProfile =
                    transaction.get(publicRef);

            if (!privateProfile.exists()) {
                throw new IllegalStateException(
                        "Private profile not found."
                );
            }

            if (!publicProfile.exists()) {
                throw new IllegalStateException(
                        "Public profile not found."
                );
            }

            // Update private nickname.
            transaction.update(
                    userRef,
                    "nickname",
                    cleanNickname
            );

            // Update public display name.
            transaction.update(
                    publicRef,
                    "displayName",
                    cleanNickname
            );

            return null;

        }).addOnSuccessListener(result ->
                onSuccess.onSuccess(null)
        ).addOnFailureListener(onFailure);
    }

    // Register a new student by atomically writing three documents.
    // Security rules must prevent existing ID reservations from
    // being overwritten and validate the linked records.
    public void registerUser(
            User user,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure) {

        if (user == null
                || user.getUid() == null
                || user.getUid().trim().isEmpty()
                || user.getUscId() == null
                || user.getUscId().trim().isEmpty()) {

            onFailure.onFailure(
                    new IllegalArgumentException(
                            "Invalid registration information."
                    )
            );
            return;
        }

        DocumentReference userRef =
                db.collection("users")
                        .document(user.getUid());

        DocumentReference idRef =
                db.collection("uscIdReservations")
                        .document(user.getUscId());

        DocumentReference publicRef =
                db.collection("publicProfiles")
                        .document(user.getUid());

        // Reserve the student's USC ID.
        Map<String, Object> reservation =
                new HashMap<>();

        reservation.put(
                "uid",
                user.getUid()
        );

        // Store approved public-facing information.
        Map<String, Object> publicProfile =
                new HashMap<>();

        publicProfile.put(
                "displayName",
                user.getNickname()
        );

        publicProfile.put(
                "fullName",
                user.getFullName()
        );

        // All three writes succeed or fail together.
        WriteBatch batch = db.batch();

        batch.set(
                idRef,
                reservation
        );

        batch.set(
                userRef,
                user
        );

        batch.set(
                publicRef,
                publicProfile
        );

        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
