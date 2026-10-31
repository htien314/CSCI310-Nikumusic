
package com.example.trojanrideshare;

import android.app.Application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class TrojanRideshareApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            // Local Firebase Authentication emulator
            FirebaseAuth.getInstance()
                    .useEmulator("10.0.2.2", 9099);

            // Local Cloud Firestore emulator
            FirebaseFirestore firestore =
                    FirebaseFirestore.getInstance();

            firestore.useEmulator("10.0.2.2", 8080);

            // Avoid reusing cached production Firestore data.
            FirebaseFirestoreSettings settings =
                    new FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(false)
                            .build();

            firestore.setFirestoreSettings(settings);
        }
    }
}
