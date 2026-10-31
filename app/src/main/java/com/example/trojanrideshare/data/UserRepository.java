
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

    // Legacy helper. Use registerUser() for new accounts
    // so USC ID uniqueness is enforced.
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

    // Retrieve a private student profile.
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
                db.collection("users").document(uid);

        DocumentReference publicRef =
                db.collection("publicProfiles").document(uid);

        db.runTransaction(transaction -> {

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

            transaction.update(
                    userRef,
                    "nickname",
                    cleanNickname
            );

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

    // Register a student and create all three Firestore records
    // in a single transaction.
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

        DocumentReference publicRef =
                db.collection("publicProfiles")
                        .document(user.getUid());

        db.runTransaction(transaction -> {

            // All transaction reads happen before writes.
            DocumentSnapshot existingId =
                    transaction.get(idRef);

            DocumentSnapshot existingUser =
                    transaction.get(userRef);

            DocumentSnapshot existingPublicProfile =
                    transaction.get(publicRef);

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

            if (existingPublicProfile.exists()) {
                throw new IllegalStateException(
                        "A public profile already exists for this account."
                );
            }

            // Reserve the USC ID.
            Map<String, Object> reservation =
                    new HashMap<>();

            reservation.put("uid", user.getUid());

            // Store only approved public information.
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
            transaction.set(idRef, reservation);
            transaction.set(userRef, user);
            transaction.set(publicRef, publicProfile);

            return null;

        }).addOnSuccessListener(result ->
                onSuccess.onSuccess(null)
        ).addOnFailureListener(onFailure);
    }
}
