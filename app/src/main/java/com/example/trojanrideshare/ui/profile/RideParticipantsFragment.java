
package com.example.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.trojanrideshare.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;

public class RideParticipantsFragment extends Fragment {

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private LinearLayout participantContainer;
    private TextView statusText;

    public RideParticipantsFragment() {
        super(R.layout.fragment_ride_participants);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        participantContainer =
                view.findViewById(R.id.participantContainer);

        statusText =
                view.findViewById(R.id.participantsStatus);

        String rideId = null;

        if (getArguments() != null) {
            rideId = getArguments().getString("rideId");
        }

        if (rideId == null || rideId.trim().isEmpty()) {
            statusText.setText("No ride selected.");
            return;
        }

        loadParticipants(rideId);
    }

    private void loadParticipants(String rideId) {

        statusText.setText("Loading participants...");
        participantContainer.removeAllViews();

        db.collection("rides")
                .document(rideId)
                .get()
                .addOnSuccessListener(ride -> {

                    if (!isAdded() || getView() == null) {
                        return;
                    }

                    if (!ride.exists()) {
                        statusText.setText("Ride not found.");
                        return;
                    }

                    Object value = ride.get("participants");

                    if (!(value instanceof List)) {
                        statusText.setText(
                                "No participants available."
                        );
                        return;
                    }

                    List<?> participants = (List<?>) value;

                    if (participants.isEmpty()) {
                        statusText.setText(
                                "No participants available."
                        );
                        return;
                    }

                    statusText.setText(
                            "Tap a rider to view their public profile."
                    );

                    for (Object item : participants) {

                        if (!(item instanceof Map)) {
                            continue;
                        }

                        Map<?, ?> participant = (Map<?, ?>) item;

                        Object uidValue =
                                participant.get("userId");

                        if (!(uidValue instanceof String)) {
                            continue;
                        }

                        String riderUid = (String) uidValue;

                        if (riderUid.trim().isEmpty()) {
                            continue;
                        }

                        Object nameValue =
                                participant.get("displayName");

                        String displayName =
                                nameValue instanceof String
                                        ? (String) nameValue
                                        : "Rider";

                        addParticipantButton(
                                displayName,
                                riderUid
                        );
                    }

                    if (participantContainer.getChildCount() == 0) {
                        statusText.setText(
                                "No participants available."
                        );
                    }
                })
                .addOnFailureListener(error -> {

                    if (!isAdded() || getView() == null) {
                        return;
                    }

                    statusText.setText(
                            "Could not load participants."
                    );
                });
    }

    private void addParticipantButton(
            String displayName,
            String riderUid) {

        Button button = new com.google.android.material.button.MaterialButton(requireContext());

        button.setText(displayName);
        button.setAllCaps(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);

        participantContainer.addView(button, params);

        button.setOnClickListener(v -> {

            Bundle args = new Bundle();
            args.putString("riderUid", riderUid);

            Navigation.findNavController(requireView())
                    .navigate(
                            R.id.publicProfileFragment,
                            args
                    );
        });
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
