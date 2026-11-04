package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.User;
import com.example.trojanrideshare.service.OrganizerIdentityProvider;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Uses Riya's private profile as the source of the organizer's name and identity. */
public final class FirebaseOrganizerIdentityProvider implements OrganizerIdentityProvider {
    private final FirebaseAuth auth;
    private final UserRepository users;
    public FirebaseOrganizerIdentityProvider(FirebaseAuth auth, UserRepository users) {
        this.auth = auth; this.users = users;
    }
    @Override public void load(Callback<User> callback) {
        FirebaseUser signedIn = auth.getCurrentUser();
        if (signedIn == null) {
            callback.onError(new IllegalStateException("Please log in to create a ride.")); return;
        }
        String uid = signedIn.getUid();
        users.getUser(uid, document -> {
            FirebaseUser current = auth.getCurrentUser();
            if (current == null || !uid.equals(current.getUid())) {
                callback.onError(new IllegalStateException("Your account changed. Please try again.")); return;
            }
            User profile = document != null && document.exists() ? document.toObject(User.class) : null;
            if (profile == null || !uid.equals(profile.getUid())) {
                callback.onError(new IllegalStateException("Your student profile could not be loaded.")); return;
            }
            callback.onSuccess(profile);
        }, callback::onError);
    }
}
