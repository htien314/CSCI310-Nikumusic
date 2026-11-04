package com.example.trojanrideshare.ui.ride;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Organizer controls always use the real Firebase session, including debug builds. */
final class CurrentUser {
    private CurrentUser() { }
    static String id() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }
}
