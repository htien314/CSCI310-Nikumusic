
package com.example.trojanrideshare;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class TrojanRideshareApp extends Application {

    // Set to true only when testing with local Firebase Emulators.
    // Keep false when sharing code with teammates.
    private static final boolean USE_FIREBASE_EMULATOR = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG && USE_FIREBASE_EMULATOR) {

            // Local Firebase Authentication Emulator
            FirebaseAuth.getInstance()
                    .useEmulator("10.0.2.2", 9099);

            // Local Cloud Firestore Emulator
            FirebaseFirestore firestore =
                    FirebaseFirestore.getInstance();

            firestore.useEmulator("10.0.2.2", 8080);

            // Disable local persistence during emulator testing.
            FirebaseFirestoreSettings settings =
                    new FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(false)
                            .build();

            firestore.setFirestoreSettings(settings);
        }
    }
}
