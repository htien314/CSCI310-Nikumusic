package com.example.trojanrideshare.ui.ride;

import com.example.trojanrideshare.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Temporary source of the logged-in user, until Riya's AuthService is ready.
 * Debug builds fall back to a test user so ride screens work before login exists.
 */
final class CurrentUser {

    private static final String DEBUG_USER_ID = "debug-user";
    private static final String DEBUG_USER_NAME = "Test User";

    private CurrentUser() {
    }

    /** The user's ID, or null if nobody is logged in. */
    static String id() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getUid();
        }
        return BuildConfig.DEBUG ? DEBUG_USER_ID : null;
    }

    static String displayName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
        }
        return DEBUG_USER_NAME;
    }
}
