package com.example.trojanrideshare.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trojanrideshare.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

        // The selected rider's UID comes from navigation.
        String riderUid = getArguments() == null
                ? null
                : getArguments().getString("riderUid");

        if (riderUid == null || riderUid.trim().isEmpty()) {
            message.setText("No rider selected.");
            return;
        }

        message.setText("Loading rider profile...");

        db.collection("publicProfiles")
                .document(riderUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    if (!document.exists()) {
                        message.setText("Public profile not found.");
                        return;
                    }

                    // Read only public-facing fields.
                    String displayName =
                            document.getString("displayName");

                    String fullName =
                            document.getString("fullName");

                    nickname.setText(
                            displayName == null ? "Rider" : displayName
                    );

                    name.setText(
                            fullName == null ? "" : fullName
                    );

                    // We won't assume payment information.
                    paymentStatus.setText(
                            "Payment status: Not available yet"
                    );

                    message.setText("");
                })
                .addOnFailureListener(error -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    message.setText(
                            "Could not load rider profile."
                    );
                });
    }
}
