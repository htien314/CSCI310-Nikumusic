package com.example.trojanrideshare.ui.ride;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.FirestoreRideRepository;
import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.CostCalculator;
import com.example.trojanrideshare.service.CostService;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompleteRideFragment extends Fragment {

    private EditText totalInput;
    private LinearLayout sharesContainer;
    private TextView errorText;
    private RadioGroup splitGroup;
    private TextView customHint;
    private Button completeButton;

    private final List<String> riderIds = new ArrayList<>();
    private final Map<String, EditText> customInputs =
            new LinkedHashMap<>();

    private RideRepository repository;
    private CostService costService;
    private String rideId;
    private boolean rideReady = false;
    private boolean saving = false;

    public CompleteRideFragment() {
        super(R.layout.fragment_complete_ride);
    }

    @Override
    public void onViewCreated(
            @NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        totalInput = view.findViewById(R.id.inputTotalCost);
        sharesContainer = view.findViewById(R.id.containerShares);
        errorText = view.findViewById(R.id.textSplitError);
        splitGroup = view.findViewById(R.id.groupSplitMethod);
        completeButton = view.findViewById(R.id.buttonCompleteRide);

        completeButton.setEnabled(false);

        // Use the same repository and clock for ride rules.
        Clock clock = System::currentTimeMillis;
        repository = new FirestoreRideRepository(
                FirebaseFirestore.getInstance());

        RideService rideService = new RideService(repository, clock);
        costService = new CostService(repository, rideService, clock);

        Bundle args = getArguments();
        rideId = args == null
                ? null : args.getString(Constants.ARG_RIDE_ID);

        splitGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updatePreview());

        totalInput.addTextChangedListener(
                watcher(this::updatePreview));

        completeButton.setOnClickListener(v -> submitCompletion());

        if (rideId == null || rideId.trim().isEmpty()) {
            showError("No ride was selected.");
            return;
        }

        showError("Loading ride participants...");
        loadRide(view);
    }

    // Load the current participants from Firebase.
    private void loadRide(View view) {
        rideReady = false;
        updateButton();

        repository.getById(rideId, new Callback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (getView() != view) return;

                if (ride == null
                        || !ride.isOrganizer(CurrentUser.id())
                        || ride.isFinished()
                        || ride.getCurrentDepartureTime() == null
                        || ride.getCurrentDepartureTime().toDate()
                        .getTime() > System.currentTimeMillis()) {
                    showError("This ride cannot be completed.");
                    return;
                }

                if (ride.getParticipants() == null
                        || ride.getParticipants().isEmpty()) {
                    showError("No participants available for this ride.");
                    return;
                }

                riderIds.clear();

                for (Participant participant : ride.getParticipants()) {
                    if (participant == null
                            || participant.getUserId() == null
                            || participant.getUserId().trim().isEmpty()
                            || riderIds.contains(participant.getUserId())) {
                        showError("This ride has invalid participants.");
                        return;
                    }

                    riderIds.add(participant.getUserId());
                }

                rideReady = true;
                updatePreview();
            }

            @Override
            public void onError(Exception error) {
                if (getView() != view) return;
                showError("Could not load ride: " + error.getMessage());
            }
        });
    }

    // Save the final cost and shares in Firebase.
    private void submitCompletion() {
        if (!rideReady || saving) return;

        long total;
        Map<String, Long> customShares = null;

        try {
            total = parseCents(totalInput.getText().toString());

            if (total <= 0) {
                throw new IllegalArgumentException(
                        "Enter a positive ride cost.");
            }

            if (splitGroup.getCheckedRadioButtonId()
                    == R.id.radioCustom) {
                customShares = getValidatedCustomShares(total);
            } else {
                CostCalculator.splitEqual(total, riderIds);
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            showError(e.getMessage() == null
                    ? "Please check the amounts."
                    : e.getMessage());
            return;
        }

        saving = true;
        updateButton();

        View currentView = getView();

        costService.completeRide(
                rideId,
                CurrentUser.id(),
                total,
                customShares,
                new Callback<Ride>() {
                    @Override
                    public void onSuccess(Ride ride) {
                        if (getView() != currentView) return;

                        saving = false;
                        rideReady = false;
                        updateButton();

                        Toast.makeText(
                                requireContext(),
                                "Ride completed successfully!",
                                Toast.LENGTH_LONG
                        ).show();

                        Bundle args = new Bundle();
                        args.putString(Constants.ARG_RIDE_ID, rideId);

                        NavHostFragment.findNavController(
                                CompleteRideFragment.this
                        ).navigate(R.id.action_global_paymentTracking, args);

                    }

                    @Override
                    public void onError(Exception error) {
                        if (getView() != currentView) return;

                        saving = false;
                        showError("Could not complete ride: "
                                + error.getMessage());
                    }
                });
    }

    // Update when a user edits an amount.
    private TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start,
                    int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count) {
                action.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
    }

    // Convert dollars into exact cents without floating-point rounding.
    private long parseCents(String value) {
        return new BigDecimal(value.trim())
                .movePointRight(2)
                .longValueExact();
    }

    private String dollars(long cents) {
        return String.format(Locale.US, "$%d.%02d",
                cents / 100, cents % 100);
    }

    // Refresh the preview while preserving custom input fields.
    private void updatePreview() {
        if (riderIds.isEmpty()) {
            sharesContainer.removeAllViews();
            customInputs.clear();
            customHint = null;
            showError("No participants available for this ride.");
            return;
        }

        long total;
        try {
            total = parseCents(totalInput.getText().toString());

            if (total <= 0) {
                showError("Enter a positive ride cost.");
                return;
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            showError("Enter a valid amount (e.g. 60.50).");
            return;
        }

        boolean custom = splitGroup.getCheckedRadioButtonId()
                == R.id.radioCustom;

        if (custom) {
            if (customInputs.isEmpty()) {
                renderCustomInputs();
            }
            customHint.setText("Total ride cost: " + dollars(total));
            validateCustomPreview(total);
        } else {
            renderEqualShares(total);
        }
    }

    // Display equal shares as readable participant rows.
    private void renderEqualShares(long total) {
        sharesContainer.removeAllViews();
        customInputs.clear();
        customHint = null;

        try {
            Map<String, Long> shares =
                    CostCalculator.splitEqual(total, riderIds);

            for (Map.Entry<String, Long> entry : shares.entrySet()) {
                TextView row = new TextView(requireContext());
                row.setText(entry.getKey() + "  •  "
                        + dollars(entry.getValue()));
                row.setTextSize(16);
                row.setTextColor(0xFF302347);
                row.setPadding(16, 20, 16, 20);
                sharesContainer.addView(row);
            }

            hideError();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // Create custom inputs once, not on every keystroke.
    private void renderCustomInputs() {
        sharesContainer.removeAllViews();
        customInputs.clear();

        for (String id : riderIds) {
            EditText input = new EditText(requireContext());
            input.setHint(id + " ($)");
            input.setSingleLine(true);
            input.setInputType(
                    InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            input.addTextChangedListener(watcher(
                    this::validateCurrentCustomInputs));

            sharesContainer.addView(input);
            customInputs.put(id, input);
        }

        customHint = new TextView(requireContext());
        customHint.setTextColor(0xFF746985);
        customHint.setPadding(0, 16, 0, 0);
        sharesContainer.addView(customHint);
    }

    // Validate custom amounts immediately when edited.
    private void validateCurrentCustomInputs() {
        try {
            long total = parseCents(
                    totalInput.getText().toString());
            validateCustomPreview(total);
        } catch (IllegalArgumentException | ArithmeticException e) {
            showError("Enter a valid total ride cost.");
        }
    }

    private void validateCustomPreview(long total) {
        try {
            getValidatedCustomShares(total);
            hideError();
        } catch (IllegalArgumentException | ArithmeticException e) {
            showError(e.getMessage() == null
                    ? "Check your custom amounts."
                    : e.getMessage());
        }
    }

    // Every rider must have a share, and shares must match the total.
    private Map<String, Long> getValidatedCustomShares(
            long totalCents) {

        Map<String, Long> shares = new LinkedHashMap<>();

        for (String riderId : riderIds) {
            EditText input = customInputs.get(riderId);

            if (input == null) {
                throw new IllegalArgumentException(
                        "Missing amount for " + riderId);
            }

            String value = input.getText().toString().trim();

            if (value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Enter a share for " + riderId);
            }

            try {
                shares.put(riderId, parseCents(value));
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new IllegalArgumentException(
                        "Enter a valid dollar amount for " + riderId);
            }
        }

        CostCalculator.validateCustom(totalCents, riderIds, shares);
        return shares;
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        updateButton();
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
        updateButton();
    }

    // Prevent invalid or duplicate submissions.
    private void updateButton() {
        if (completeButton != null) {
            completeButton.setEnabled(
                    rideReady && !saving
                            && errorText.getVisibility() != View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        customInputs.clear();
        riderIds.clear();
        customHint = null;
        rideReady = false;
        saving = false;
        completeButton = null;
        super.onDestroyView();
    }
}
