
package com.example.trojanrideshare.ui.ride;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.service.CostCalculator;

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

    private final List<String> riderIds = new ArrayList<>();
    private final Map<String, EditText> customInputs =
            new LinkedHashMap<>();

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

        // Firebase completion will be connected later.
        view.findViewById(R.id.buttonCompleteRide)
                .setEnabled(false);

        // Participants must come from the selected ride.
        riderIds.clear();
        Bundle args = getArguments();
        if (args != null) {
            ArrayList<String> ids =
                    args.getStringArrayList("riderIds");
            if (ids != null) riderIds.addAll(ids);
        }

        splitGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updatePreview());

        totalInput.addTextChangedListener(
                watcher(this::updatePreview));

        updatePreview();
    }

    // Listen for changes to the entered ride cost.
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

    // Convert dollars to exact integer cents.
    private long parseCents(String value) {
        return new BigDecimal(value.trim())
                .movePointRight(2)
                .longValueExact();
    }

    // Refresh the participant cost preview.
    private void updatePreview() {
        sharesContainer.removeAllViews();

        try {
            if (riderIds.isEmpty()) {
                customInputs.clear();
                showError("No participants available for this ride.");
                return;
            }

            long total = parseCents(
                    totalInput.getText().toString());

            if (total <= 0) {
                showError("Enter a positive ride cost.");
                return;
            }

            boolean custom =
                    splitGroup.getCheckedRadioButtonId()
                            == R.id.radioCustom;

            if (custom) {
                renderCustomInputs(total);
            } else {
                renderEqualShares(total);
            }

        } catch (IllegalArgumentException | ArithmeticException e) {
            showError("Enter a valid amount (e.g. 60.50).");
        }
    }

    // Display each participant's equal share.
    private void renderEqualShares(long total) {
        customInputs.clear();

        Map<String, Long> shares =
                CostCalculator.splitEqual(total, riderIds);

        for (Map.Entry<String, Long> entry : shares.entrySet()) {
            TextView row = new TextView(requireContext());

            row.setText(String.format(
                    Locale.US, "%s  •  $%.2f",
                    entry.getKey(),
                    entry.getValue() / 100.0));

            row.setTextSize(16);
            row.setTextColor(0xFF302347);
            row.setPadding(16, 20, 16, 20);

            sharesContainer.addView(row);
        }

        hideError();
    }

    // Show editable amounts for custom splitting.
    private void renderCustomInputs(long total) {
        Map<String, String> previous = new LinkedHashMap<>();

        for (Map.Entry<String, EditText> entry
                : customInputs.entrySet()) {
            previous.put(entry.getKey(),
                    entry.getValue().getText().toString());
        }

        customInputs.clear();

        for (String id : riderIds) {
            EditText input = new EditText(requireContext());

            input.setHint(id + " ($)");
            input.setSingleLine(true);
            input.setInputType(
                    InputType.TYPE_CLASS_NUMBER
                            | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            String saved = previous.get(id);
            if (saved != null) input.setText(saved);

            sharesContainer.addView(input);
            customInputs.put(id, input);
        }

        TextView hint = new TextView(requireContext());
        hint.setText("Custom shares must total "
                + String.format(Locale.US, "$%.2f",
                total / 100.0));
        hint.setTextColor(0xFF746985);
        hint.setPadding(0, 16, 0, 0);

        sharesContainer.addView(hint);
        hideError();
    }

    // Check that custom shares add up to the ride total.
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

        CostCalculator.validateCustom(
                totalCents, riderIds, shares);

        return shares;
    }

    // Display or dismiss validation messages.
    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        customInputs.clear();
        super.onDestroyView();
    }
}
