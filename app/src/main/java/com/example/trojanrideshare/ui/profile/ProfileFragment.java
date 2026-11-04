
package com.example.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileFragment extends Fragment {

    private final AuthService authService = new AuthService();
    private final UserRepository userRepository =
            new UserRepository();
    private final HistoryRepository historyRepository =
            new HistoryRepository();

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
    }

    /** Rebind the current account whenever this tab is restored from navigation. */
    private void renderAccount(View view) {
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

        TextView message =
                view.findViewById(R.id.profileMessage);

        TextView upcomingRides =
                view.findViewById(R.id.profileUpcomingRides);

        EditText nickname =
                view.findViewById(R.id.profileNickname);

        LinearLayout sharingContainer =
                view.findViewById(R.id.profileSharingContainer);

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

        // Login and registration navigation.
        login.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.loginFragment)
        );

        register.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.registerFragment)
        );

        // Open Ride History.
        viewHistory.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.historyFragment)
        );

        // Log out must not depend on a successful profile/database response.
        logout.setOnClickListener(v -> {
            authService.logout();
            fullName.setText(""); email.setText(""); uscId.setText(""); graduationYear.setText("");
            nickname.setText(""); upcomingRides.setText(""); sharingContainer.removeAllViews();
            logout.setVisibility(View.GONE);
            view.findViewById(R.id.profilePaymentFlags).setVisibility(View.GONE);
            showSignedOut(signedIn, signedOut);
            showMessage(message, "You have logged out. You can log in or register another account.");
        });

        FirebaseUser currentUser = authService.getCurrentUser();
        logout.setVisibility(currentUser == null ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.profilePaymentFlags).setVisibility(currentUser == null ? View.GONE : View.VISIBLE);

        if (currentUser == null) {
            showSignedOut(signedIn, signedOut);
            showMessage(message, "");
            return;
        }

        String uid = currentUser.getUid();

        signedOut.setVisibility(View.GONE);
        signedIn.setVisibility(View.GONE);

        showMessage(message, "Loading your profile...");

        // Load private account information.
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

                    // Load upcoming rides and public sharing options.
                    loadRidesAndSharing(
                            view,
                            uid,
                            upcomingRides,
                            sharingContainer
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

        // Save nickname in private and public profiles.
        saveNickname.setOnClickListener(v -> {

            if (!isCurrentUser(view, uid)) {
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

    }

    @Override public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) renderAccount(view);
    }

    // Load user's public sharing preferences and all their rides.
    private void loadRidesAndSharing(
            View view,
            String uid,
            TextView upcomingRides,
            LinearLayout sharingContainer) {

        upcomingRides.setText("Loading upcoming rides...");

        setContainerMessage(
                sharingContainer,
                "Loading sharing preferences..."
        );

        db.collection("publicProfiles")
                .document(uid)
                .get()
                .addOnSuccessListener(publicDoc -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    Object flags=publicDoc.get("overdueRideIds");
                    int flagCount=flags instanceof List ? ((List<?>)flags).size() : 0;
                    ((TextView)view.findViewById(R.id.profilePaymentFlags)).setText(flagCount==0
                            ? "No overdue payment flags." : "Overdue payment flags: " + flagCount);
                    Set<String> sharedIds = new HashSet<>();

                    if (publicDoc.exists()) {
                        Object raw = publicDoc.get("sharedRideIds");

                        if (raw instanceof List) {
                            for (Object item : (List<?>) raw) {
                                if (item instanceof String) {
                                    sharedIds.add((String) item);
                                }
                            }
                        }
                    }

                    historyRepository.getMyRides(
                            uid,
                            rides -> {

                                if (!isCurrentUser(view, uid)) {
                                    return;
                                }

                                showUpcomingRides(
                                        rides,
                                        upcomingRides
                                );

                                showSharingOptions(
                                        view,
                                        uid,
                                        rides,
                                        sharedIds,
                                        sharingContainer
                                );
                            },
                            error -> {

                                if (!isCurrentUser(view, uid)) {
                                    return;
                                }

                                upcomingRides.setText(
                                        "Could not load upcoming rides."
                                );

                                setContainerMessage(
                                        sharingContainer,
                                        "Could not load rides."
                                );
                            }
                    );
                })
                .addOnFailureListener(error -> {

                    if (!isCurrentUser(view, uid)) {
                        return;
                    }

                    upcomingRides.setText(
                            "Could not load sharing preferences."
                    );

                    setContainerMessage(
                            sharingContainer,
                            "Could not load sharing preferences."
                    );
                });
    }

    // Display upcoming organized and joined rides.
    private void showUpcomingRides(
            List<DocumentSnapshot> rides,
            TextView upcomingRides) {

        List<DocumentSnapshot> upcoming = new ArrayList<>();

        for (DocumentSnapshot ride : rides) {

            if (isUpcoming(ride)) {
                upcoming.add(ride);
            }
        }

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

            text.append(getTitle(ride))
                    .append("\n")
                    .append(getAddress(ride, "origin"))
                    .append(" \u2192 ")
                    .append(getAddress(ride, "destination"))
                    .append("\n")
                    .append(
                            formatter.format(
                                    getDeparture(ride).toDate()
                            )
                    )
                    .append("\n\n");
        }

        upcomingRides.setText(
                text.toString().trim()
        );
    }

    // Display a sharing checkbox for each eligible ride.
    private void showSharingOptions(
            View view,
            String uid,
            List<DocumentSnapshot> rides,
            Set<String> sharedIds,
            LinearLayout container) {

        container.removeAllViews();

        List<DocumentSnapshot> eligible = new ArrayList<>();

        for (DocumentSnapshot ride : rides) {

            if (isUpcoming(ride)
                    || "COMPLETED".equals(
                    ride.getString("activityStatus")
            )) {

                eligible.add(ride);
            }
        }

        eligible.sort(
                Comparator.comparingLong(
                        (DocumentSnapshot ride) -> {
                            Timestamp departure = getDeparture(ride);
                            return departure == null
                                    ? 0L
                                    : departure.toDate().getTime();
                        }
                ).reversed()
        );

        if (eligible.isEmpty()) {
            setContainerMessage(
                    container,
                    "No upcoming or completed rides available to share."
            );
            return;
        }

        for (DocumentSnapshot ride : eligible) {

            final String rideId = ride.getId();

            final boolean upcoming = isUpcoming(ride);

            CheckBox checkbox = new CheckBox(requireContext());

            checkbox.setText(
                    getTitle(ride)
                            + "\n"
                            + (upcoming ? "Upcoming" : "Completed")
                            + " - Share on public profile"
            );

            checkbox.setChecked(sharedIds.contains(rideId));

            container.addView(checkbox);
            Button details = new com.google.android.material.button.MaterialButton(requireContext());
            details.setText(R.string.profile_open_ride_details);
            details.setOnClickListener(v -> {
                if (!isCurrentUser(view, uid)) return;
                Bundle args = new Bundle();
                args.putString("rideId", rideId);
                Navigation.findNavController(view).navigate(R.id.action_global_rideDetails, args);
            });
            container.addView(details);

            checkbox.setOnCheckedChangeListener(
                    (button, checked) -> {

                        if (!isCurrentUser(view, uid)) {
                            return;
                        }

                        checkbox.setEnabled(false);

                        userRepository.setRideShared(
                                uid,
                                rideId,
                                checked,
                                unused -> {

                                    if (!isCurrentUser(view, uid)) {
                                        return;
                                    }

                                    checkbox.setEnabled(true);

                                    Toast.makeText(
                                            requireContext(),
                                            checked
                                                    ? "Ride shared on public profile"
                                                    : "Ride removed from public profile",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                },
                                error -> {

                                    if (!isCurrentUser(view, uid)) {
                                        return;
                                    }

                                    android.util.Log.e(
                                            "RIDE_SHARING",
                                            "Failed to save sharing preference",
                                            error
                                    );

                                    Toast.makeText(
                                            requireContext(),
                                            "Sharing error: " + error.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();

                                    // Reload from Firestore so the checkbox
                                    // reflects the actual saved state.
                                    loadRidesAndSharing(
                                            view,
                                            uid,
                                            view.findViewById(
                                                    R.id.profileUpcomingRides
                                            ),
                                            container
                                    );
                                }
                        );
                    }
            );
        }
    }

    // Only active rides with future departures count as upcoming.
    private boolean isUpcoming(DocumentSnapshot ride) {

        String status = ride.getString("activityStatus");

        if (!"ON_TIME".equals(status)
                && !"DELAYED".equals(status)) {
            return false;
        }

        Timestamp departure = getDeparture(ride);

        return departure != null
                && departure.toDate().after(new Date());
    }

    // Use delayed departure time when available.
    private Timestamp getDeparture(DocumentSnapshot ride) {

        Timestamp delayed =
                ride.getTimestamp("delayedDepartureTime");

        return delayed != null
                ? delayed
                : ride.getTimestamp("departureTime");
    }

    private String getTitle(DocumentSnapshot ride) {

        String title = ride.getString("title");

        if (title == null || title.trim().isEmpty()) {
            return "Untitled Ride";
        }

        return title;
    }

    // Read addresses from Firestore location maps.
    private String getAddress(
            DocumentSnapshot ride,
            String fieldName) {

        Object value = ride.get(fieldName);

        if (value instanceof Map) {

            Object address =
                    ((Map<?, ?>) value).get("address");

            if (address instanceof String) {
                return (String) address;
            }
        }

        return "Unknown location";
    }

    private void setContainerMessage(
            LinearLayout container,
            String text) {

        container.removeAllViews();

        TextView message = new TextView(requireContext());
        message.setText(text);
        container.addView(message);
    }

    private boolean isCurrentUser(View view, String uid) {

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
