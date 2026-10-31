
package com.example.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.UserRepository;
import com.example.trojanrideshare.model.User;
import com.example.trojanrideshare.service.AuthService;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private final AuthService authService = new AuthService();
    private final UserRepository userRepository = new UserRepository();

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        LinearLayout signedIn =
                view.findViewById(R.id.profileSignedIn);

        LinearLayout signedOut =
                view.findViewById(R.id.profileSignedOut);

        TextView fullName =
                view.findViewById(R.id.profileFullName);

        TextView email =
                view.findViewById(R.id.profileEmail);

        TextView uscId =
                view.findViewById(R.id.profileUscId);

        TextView graduationYear =
                view.findViewById(R.id.profileGraduationYear);

        EditText nickname =
                view.findViewById(R.id.profileNickname);

        TextView message =
                view.findViewById(R.id.profileMessage);

        Button saveNickname =
                view.findViewById(R.id.profileSaveNicknameButton);

        Button logout =
                view.findViewById(R.id.profileLogoutButton);

        Button login =
                view.findViewById(R.id.profileLoginButton);

        Button register =
                view.findViewById(R.id.profileRegisterButton);

        // Navigate to Login when signed out
        login.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.loginFragment)
        );

        // Navigate to Registration when signed out
        register.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.registerFragment)
        );

        FirebaseUser currentUser = authService.getCurrentUser();

        // Show login and registration options if signed out
        if (currentUser == null) {
            signedIn.setVisibility(View.GONE);
            signedOut.setVisibility(View.VISIBLE);
            message.setText("");
            return;
        }

        signedOut.setVisibility(View.GONE);
        signedIn.setVisibility(View.GONE);
        message.setText("Loading your profile...");

        String uid = currentUser.getUid();

        // Load the user's profile from Firestore
        userRepository.getUser(
                uid,
                document -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    FirebaseUser activeUser =
                            authService.getCurrentUser();

                    if (activeUser == null ||
                            !uid.equals(activeUser.getUid())) {
                        return;
                    }

                    if (document == null || !document.exists()) {
                        message.setText("Profile not found.");
                        return;
                    }

                    // Convert Firestore data to a User object
                    User user = document.toObject(User.class);

                    if (user == null) {
                        message.setText("Could not read profile.");
                        return;
                    }

                    fullName.setText(user.getFullName());

                    email.setText(
                            "USC Email: " + user.getUscEmail()
                    );

                    uscId.setText(
                            "USC ID: " + user.getUscId()
                    );

                    graduationYear.setText(
                            "Graduation Year: " +
                                    user.getGraduationYear()
                    );

                    nickname.setText(user.getNickname());

                    signedIn.setVisibility(View.VISIBLE);
                    message.setText("");
                },
                error -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    message.setText(
                            "Could not load profile. Please try again."
                    );
                }
        );

        // Save nickname changes
        saveNickname.setOnClickListener(v -> {

            String newNickname =
                    nickname.getText().toString().trim();

            if (newNickname.isEmpty()) {
                message.setText("Nickname cannot be empty.");
                return;
            }

            FirebaseUser activeUser =
                    authService.getCurrentUser();

            if (activeUser == null ||
                    !uid.equals(activeUser.getUid())) {
                message.setText("Please log in again.");
                return;
            }

            saveNickname.setEnabled(false);
            message.setText("Saving nickname...");

            userRepository.updateNickname(
                    uid,
                    newNickname,
                    unused -> {
                        if (!isAdded() || getView() != view) {
                            return;
                        }

                        saveNickname.setEnabled(true);
                        message.setText(
                                "Nickname updated successfully!"
                        );
                    },
                    error -> {
                        if (!isAdded() || getView() != view) {
                            return;
                        }

                        saveNickname.setEnabled(true);
                        message.setText(
                                "Could not update nickname."
                        );
                    }
            );
        });

        // Log out of Firebase
        logout.setOnClickListener(v -> {

            authService.logout();

            signedIn.setVisibility(View.GONE);
            signedOut.setVisibility(View.VISIBLE);

            nickname.setText("");
            message.setText("You have logged out.");
        });
    }
}
