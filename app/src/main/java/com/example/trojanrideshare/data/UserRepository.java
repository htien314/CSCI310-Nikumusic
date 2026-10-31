
package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {

    private final FirebaseFirestore db;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // Save a student profile after successful registration.
    public void saveUser(User user,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {

        db.collection("users")
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Retrieve a student profile by Firebase UID.
    public void getUser(String uid,
                        OnSuccessListener<DocumentSnapshot> onSuccess,
                        OnFailureListener onFailure) {

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Update the student's nickname.
    public void updateNickname(String uid,
                               String nickname,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {

        db.collection("users")
                .document(uid)
                .update("nickname", nickname)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
