
package com.example.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trojanrideshare.R;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PublicProfileFragment extends Fragment {

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    public PublicProfileFragment() {
        super(R.layout.fragment_public_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        TextView nickname =
                view.findViewById(R.id.publicProfileNickname);
        TextView name =
                view.findViewById(R.id.publicProfileName);
        TextView paymentStatus =
                view.findViewById(R.id.publicProfilePaymentStatus);
        TextView message =
                view.findViewById(R.id.publicProfileMessage);
        TextView sharedRides =
                view.findViewById(R.id.publicProfileSharedRides);

        String riderUid = getArguments() == null
                ? null
                : getArguments().getString("riderUid");

        if (riderUid == null || riderUid.trim().isEmpty()) {
            message.setText("No rider selected.");
            sharedRides.setText("");
            return;
        }

        message.setText("Loading rider profile...");
        sharedRides.setText("Loading shared rides...");

        db.collection("publicProfiles")
                .document(riderUid)
                .get()
                .addOnSuccessListener(document -> {

                    if (!isActiveView(view)) {
                        return;
                    }

                    if (!document.exists()) {
                        message.setText("Public profile not found.");
                        sharedRides.setText("");
                        return;
                    }

                    String displayName =
                            document.getString("displayName");

                    String fullName =
                            document.getString("fullName");

                    nickname.setText(
                            displayName == null
                                    ? "Rider"
                                    : displayName
                    );

                    name.setText(fullName == null ? "" : fullName);

                    Object flags=document.get("overdueRideIds");
                    int count=flags instanceof List ? ((List<?>)flags).size() : 0;
                    paymentStatus.setText(count==0 ? "No overdue payment flags." : "Overdue payment flags: " + count);

                    message.setText("");

                    loadSharedRides(
                            view,
                            riderUid,
                            document,
                            sharedRides
                    );
                })
                .addOnFailureListener(error -> {
                    if (!isActiveView(view)) {
                        return;
                    }
                    message.setText("Could not load rider profile.");
                    sharedRides.setText("");
                });
    }

    private void loadSharedRides(
            View view,
            String riderUid,
            DocumentSnapshot publicProfile,
            TextView sharedRides) {

        Object rawIds = publicProfile.get("sharedRideIds");

        if (!(rawIds instanceof List)) {
            sharedRides.setText("No rides shared publicly.");
            return;
        }

        List<?> ids = (List<?>) rawIds;
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();

        for (Object item : ids) {
            if (!(item instanceof String)) {
                continue;
            }

            String rideId = (String) item;

            if (rideId.isEmpty()) {
                continue;
            }

            tasks.add(
                    db.collection("rides")
                            .document(rideId)
                            .get()
            );
        }

        if (tasks.isEmpty()) {
            sharedRides.setText("No rides shared publicly.");
            return;
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {

                    if (!isActiveView(view)) {
                        return;
                    }

                    List<DocumentSnapshot> visible = new ArrayList<>();

                    for (Object item : results) {
                        DocumentSnapshot ride =
                                (DocumentSnapshot) item;

                        if (!ride.exists()) {
                            continue;
                        }

                        // Recheck that the ride belongs to this
                        // rider before displaying it.
                        if (!isParticipantOrOrganizer(ride, riderUid)) {
                            continue;
                        }

                        String status =
                                ride.getString("activityStatus");

                        boolean completed =
                                "COMPLETED".equals(status);

                        Timestamp departure = getDeparture(ride);

                        boolean upcoming =
                                ("ON_TIME".equals(status)
                                        || "DELAYED".equals(status))
                                        && departure != null
                                        && departure.toDate().after(new Date());

                        if (completed || upcoming) {
                            visible.add(ride);
                        }
                    }

                    if (visible.isEmpty()) {
                        sharedRides.setText("No rides shared publicly.");
                        return;
                    }

                    visible.sort(
                            Comparator.comparingLong(
                                    (DocumentSnapshot ride) -> {
                                        Timestamp departure =
                                                getDeparture(ride);

                                        return departure == null
                                                ? 0
                                                : departure.toDate()
                                                .getTime();
                                    }
                            ).reversed()
                    );

                    DateFormat dateFormat =
                            DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT
                            );

                    StringBuilder output = new StringBuilder();

                    for (DocumentSnapshot ride : visible) {

                        String title = ride.getString("title");
                        if (title == null || title.isEmpty()) {
                            title = "Untitled Ride";
                        }

                        String status =
                                ride.getString("activityStatus");

                        String label =
                                "COMPLETED".equals(status)
                                        ? "Completed"
                                        : "Upcoming";

                        output.append(title)
                                .append("\n")
                                .append(label)
                                .append("\n")
                                .append(getAddress(ride, "origin"))
                                .append(" \u2192 ")
                                .append(getAddress(ride, "destination"));

                        Timestamp departure = getDeparture(ride);

                        if (departure != null) {
                            output.append("\n")
                                    .append(dateFormat.format(
                                            departure.toDate()
                                    ));
                        }

                        output.append("\n\n");
                    }

                    sharedRides.setText(
                            output.toString().trim()
                    );
                })
                .addOnFailureListener(error -> {
                    if (isActiveView(view)) {
                        sharedRides.setText(
                                "Could not load shared rides."
                        );
                    }
                });
    }

    private boolean isParticipantOrOrganizer(
            DocumentSnapshot ride,
            String riderUid) {

        if (riderUid.equals(ride.getString("organizerId"))) {
            return true;
        }

        Object rawParticipants = ride.get("participants");

        if (!(rawParticipants instanceof List)) {
            return false;
        }

        for (Object item : (List<?>) rawParticipants) {
            if (!(item instanceof Map)) {
                continue;
            }

            Object userId =
                    ((Map<?, ?>) item).get("userId");

            if (riderUid.equals(userId)) {
                return true;
            }
        }

        return false;
    }

    private Timestamp getDeparture(DocumentSnapshot ride) {
        Timestamp delayed =
                ride.getTimestamp("delayedDepartureTime");

        return delayed != null
                ? delayed
                : ride.getTimestamp("departureTime");
    }

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

    private boolean isActiveView(View view) {
        return isAdded() && getView() == view;
    }
}
