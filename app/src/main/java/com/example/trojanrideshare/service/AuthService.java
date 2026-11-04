
package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.UserRepository;
import com.example.trojanrideshare.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class AuthService {

    private final FirebaseAuth auth;
    private final UserRepository userRepository;

    public AuthService() {
        auth = FirebaseAuth.getInstance();
        userRepository = new UserRepository();
    }

    // Validate registration information.
    // Returns null if all information is valid.
    public String validateRegistration(
            String fullName,
            String email,
            String uscId,
            String password,
            int graduationYear) {

        return RegistrationValidator.validate(
                fullName,
                email,
                uscId,
                password,
                graduationYear
        );
    }

    // Register a new student using Firebase.
    public void register(
            String fullName,
            String nickname,
            String email,
            String uscId,
            String password,
            int graduationYear,
            OnSuccessListener<User> onSuccess,
            OnFailureListener onFailure) {

        String validationError = validateRegistration(
                fullName,
                email,
                uscId,
                password,
                graduationYear
        );

        if (validationError != null) {
            onFailure.onFailure(
                    new IllegalArgumentException(validationError)
            );
            return;
        }

        String cleanName = fullName.trim();
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        String cleanId = uscId.trim();

        String displayName =
                (nickname == null || nickname.trim().isEmpty())
                        ? cleanName
                        : nickname.trim();

        // Step 1: Create the Firebase Authentication account.
        auth.createUserWithEmailAndPassword(
                cleanEmail,
                password
        ).addOnSuccessListener(authResult -> {

            FirebaseUser firebaseUser = authResult.getUser();

            if (firebaseUser == null) {
                onFailure.onFailure(
                        new IllegalStateException(
                                "Firebase did not return a user."
                        )
                );
                return;
            }

            // Step 2: Build the student profile.
            User user = new User(
                    firebaseUser.getUid(),
                    cleanName,
                    displayName,
                    cleanEmail,
                    cleanId,
                    graduationYear
            );

            // Step 3: Atomically save the profile and USC ID.
            userRepository.registerUser(
                    user,
                    unused -> onSuccess.onSuccess(user),
                    error -> {

                        // Profile creation failed.
                        // Attempt to delete the new Auth account.
                        firebaseUser.delete()
                                .addOnSuccessListener(unused -> {
                                    auth.signOut();
                                    onFailure.onFailure(error);
                                })
                                .addOnFailureListener(deleteError -> {
                                    auth.signOut();

                                    onFailure.onFailure(
                                            new IllegalStateException(
                                                    "Registration failed, and "
                                                            + "account cleanup also failed. "
                                                            + "The account may need "
                                                            + "manual recovery.",
                                                    error
                                            )
                                    );
                                });
                    }
            );

        }).addOnFailureListener(error -> {

            if (error instanceof FirebaseAuthUserCollisionException) {
                onFailure.onFailure(
                        new IllegalArgumentException(
                                "This USC email is already registered."
                        )
                );
            } else {
                onFailure.onFailure(error);
            }
        });
    }

    // Log in an existing student.
    public void login(
            String email,
            String password,
            OnSuccessListener<AuthResult> onSuccess,
            OnFailureListener onFailure) {

        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {

            onFailure.onFailure(
                    new IllegalArgumentException(
                            "Email and password are required."
                    )
            );
            return;
        }

        auth.signInWithEmailAndPassword(
                        email.trim(),
                        password
                )
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // Log out.
    public void logout() {
        auth.signOut();
    }

    // Get the current Firebase user.
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    // Check login status.
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
}
