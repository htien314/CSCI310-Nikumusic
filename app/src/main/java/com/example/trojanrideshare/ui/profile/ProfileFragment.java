
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
import com.example.trojanrideshare.data.HistoryRepository;
import com.example.trojanrideshare.data.UserRepository;
import com.example.trojanrideshare.model.User;
import com.example.trojanrideshare.service.AuthService;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private final AuthService authService = new AuthService();
    private final UserRepository userRepository = new UserRepository();
    private final HistoryRepository historyRepository =
            new HistoryRepository();

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

        TextView upcomingRides =
                view.findViewById(R.id.profileUpcomingRides);

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

        Button viewHistory =
                view.findViewById(R.id.profileViewHistoryButton);

        // Signed-out navigation
        login.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.loginFragment)
        );

        register.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.registerFragment)
        );

        // Ride History navigation
        viewHistory.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.historyFragment)
        );

        FirebaseUser currentUser = authService.getCurrentUser();

        if (currentUser == null) {
            showSignedOut(signedIn, signedOut);
            showMessage(message, "");
            return;
        }

        String uid = currentUser.getUid();

        signedOut.setVisibility(View.GONE);
        signedIn.setVisibility(View.GONE);
        showMessage(message, "Loading your profile...");

        // Load the current user's private profile.
        userRepository.getUser(
                uid,
                document -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    if (document == null || !document.exists()) {
                        showMessage(message, "Profile not found.");
                        return;
                    }

                    User user = document.toObject(User.class);

                    if (user == null) {
                        showMessage(message, "Could not read profile.");
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
                            "Graduation Year: " + user.getGraduationYear()
                    );

                    nickname.setText(user.getNickname());

                    signedIn.setVisibility(View.VISIBLE);
                    showMessage(message, "");

                    loadUpcomingRides(
                            view,
                            uid,
                            upcomingRides
                    );
                },
                error -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    showMessage(
                            message,
                            "Could not load profile. Please try again."
                    );
                }
        );

        // Update nickname in private and public profiles.
        saveNickname.setOnClickListener(v -> {

            if (!isCurrentUser(view, uid)) {
                showMessage(message, "Please log in again.");
                return;
            }

            String newNickname =
                    nickname.getText().toString().trim();

            if (newNickname.isEmpty()) {
                showMessage(
                        message,
                        "Nickname cannot be empty."
                );
                return;
            }

            saveNickname.setEnabled(false);
            showMessage(message, "Saving nickname...");

            userRepository.updateNickname(
                    uid,
                    newNickname,
                    unused -> {

                        if (!isCurrentUser(view, uid)) {
                            return;
                        }

                        saveNickname.setEnabled(true);
                        showMessage(
                                message,
                                "Nickname updated successfully!"
                        );
                    },
                    error -> {

                        if (!isCurrentUser(view, uid)) {
                            return;
                        }

                        saveNickname.setEnabled(true);
                        showMessage(
                                message,
                                "Could not update nickname."
                        );
                    }
            );
        });

        // Log out.
        logout.setOnClickListener(v -> {

            authService.logout();

            nickname.setText("");
            upcomingRides.setText("");

            showSignedOut(signedIn, signedOut);
            showMessage(message, "You have logged out.");
        });
    }

    // Load upcoming organized and joined rides.
    private void loadUpcomingRides(
            View view,
            String uid,
            TextView upcomingRides) {

        upcomingRides.setText("Loading upcoming rides...");

        historyRepository.getMyRides(
                uid,
                documents -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    List<DocumentSnapshot> upcoming =
                            new ArrayList<>();

                    for (DocumentSnapshot document : documents) {

                        String status =
                                document.getString("activityStatus");

                        // Completed and cancelled rides belong in History.
                        if ("COMPLETED".equals(status)
                                || "CANCELLED".equals(status)) {
                            continue;
                        }

                        Timestamp departure =
                                getDeparture(document);

                        // Only show rides with future departure times.
                        if (departure == null
                                || !departure.toDate().after(new Date())) {
                            continue;
                        }

                        upcoming.add(document);
                    }

                    // Sort by soonest departure.
                    upcoming.sort(
                            Comparator.comparingLong(
                                    ride -> getDeparture(ride)
                                            .toDate()
                                            .getTime()
                            )
                    );

                    if (upcoming.isEmpty()) {
                        upcomingRides.setText("No upcoming rides.");
                        return;
                    }

                    StringBuilder text = new StringBuilder();

                    DateFormat formatter =
                            DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT
                            );

                    for (DocumentSnapshot ride : upcoming) {

                        String title = ride.getString("title");

                        if (title == null
                                || title.trim().isEmpty()) {
                            title = "Untitled Ride";
                        }

                        String origin =
                                getAddress(ride, "origin");

                        String destination =
                                getAddress(ride, "destination");

                        Timestamp departure =
                                getDeparture(ride);

                        text.append(title)
                                .append("\n")
                                .append(origin)
                                .append(" \u2192 ")
                                .append(destination)
                                .append("\n")
                                .append(formatter.format(
                                        departure.toDate()
                                ))
                                .append("\n\n");
                    }

                    upcomingRides.setText(
                            text.toString().trim()
                    );
                },
                error -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    upcomingRides.setText(
                            "Could not load upcoming rides."
                    );
                }
        );
    }

    // Use the delayed departure time if one exists.
    private Timestamp getDeparture(DocumentSnapshot ride) {

        Timestamp delayed =
                ride.getTimestamp("delayedDepartureTime");

        if (delayed != null) {
            return delayed;
        }

        return ride.getTimestamp("departureTime");
    }

    // Read an address from a Firestore location map.
    private String getAddress(
            DocumentSnapshot ride,
            String fieldName) {

        Object value = ride.get(fieldName);

        if (value instanceof Map) {

            Map<?, ?> location = (Map<?, ?>) value;

            Object address = location.get("address");

            if (address instanceof String) {
                return (String) address;
            }
        }

        return "Unknown location";
    }

    // Ensure asynchronous results belong to this screen/user.
    private boolean isCurrentUser(
            View view,
            String uid) {

        if (!isAdded() || getView() != view) {
            return false;
        }

        FirebaseUser activeUser =
                authService.getCurrentUser();

        return activeUser != null
                && uid.equals(activeUser.getUid());
    }

    private void showSignedOut(
            LinearLayout signedIn,
            LinearLayout signedOut) {

        signedIn.setVisibility(View.GONE);
        signedOut.setVisibility(View.VISIBLE);
    }

    private void showMessage(
            TextView message,
            String text) {

        message.setText(text);
        message.setVisibility(
                text == null || text.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );
    }
}
