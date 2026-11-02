package com.example.trojanrideshare.ui.ride;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.FirestoreMonthlySummaryRepository;
import com.example.trojanrideshare.model.MonthlySummary;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.MonthlySummaryService;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MonthlySummaryFragment extends Fragment {

    private static final String KEY_MONTH = "selected_month";

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private YearMonth selectedMonth;
    private FirestoreMonthlySummaryRepository repository;
    private MonthlySummaryService service;

    private TextView monthText;
    private TextView rideCountText;
    private TextView organizedText;
    private TextView joinedText;
    private TextView totalCostText;
    private TextView myShareText;
    private TextView paidText;
    private TextView unpaidText;
    private TextView statusText;
    private TextView errorText;

    private int requestVersion;

    public MonthlySummaryFragment() {
        super(R.layout.fragment_monthly_summary);
    }

    @Override
    public void onViewCreated(
            @NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        monthText = view.findViewById(R.id.textSelectedMonth);
        rideCountText = view.findViewById(R.id.textRideCount);
        organizedText = view.findViewById(R.id.textOrganizedCount);
        joinedText = view.findViewById(R.id.textJoinedCount);
        totalCostText = view.findViewById(R.id.textTotalCost);
        myShareText = view.findViewById(R.id.textMyShare);
        paidText = view.findViewById(R.id.textPaidAmount);
        unpaidText = view.findViewById(R.id.textUnpaidAmount);
        statusText = view.findViewById(R.id.textMonthlyStatus);
        errorText = view.findViewById(R.id.textMonthlyError);

        Button previous = view.findViewById(R.id.buttonPreviousMonth);
        Button next = view.findViewById(R.id.buttonNextMonth);

        repository = new FirestoreMonthlySummaryRepository(
                FirebaseFirestore.getInstance());
        service = new MonthlySummaryService();

        if (state != null && state.containsKey(KEY_MONTH)) {
            try {
                selectedMonth = YearMonth.parse(
                        state.getString(KEY_MONTH));
            } catch (Exception exception) {
                selectedMonth = YearMonth.now(Constants.DISPLAY_ZONE);
            }
        } else {
            selectedMonth = YearMonth.now(Constants.DISPLAY_ZONE);
        }

        previous.setOnClickListener(v -> {
            selectedMonth = selectedMonth.minusMonths(1);
            loadSummary();
        });

        next.setOnClickListener(v -> {
            selectedMonth = selectedMonth.plusMonths(1);
            loadSummary();
        });

        loadSummary();
    }

    // Fetch rides for the selected month.
    private void loadSummary() {
        int currentRequest = ++requestVersion;

        monthText.setText(selectedMonth.format(MONTH_FORMAT));
        statusText.setText("Loading monthly summary...");
        errorText.setVisibility(View.GONE);

        String userId = CurrentUser.id();

        if (userId == null || userId.trim().isEmpty()) {
            showError("Please sign in to see your summary.");
            return;
        }

        View screen = getView();
        YearMonth requestedMonth = selectedMonth;

        repository.getRidesForUser(userId,
                new Callback<List<Ride>>() {
                    @Override
                    public void onSuccess(List<Ride> rides) {
                        if (getView() != screen
                                || currentRequest != requestVersion
                                || !requestedMonth.equals(selectedMonth)) {
                            return;
                        }

                        MonthlySummary result = service.calculate(
                                rides, userId, requestedMonth);

                        showSummary(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (getView() != screen
                                || currentRequest != requestVersion
                                || !requestedMonth.equals(selectedMonth)) {
                            return;
                        }

                        showError("Could not load summary: "
                                + error.getMessage());
                    }
                });
    }

    // Display all monthly totals.
    private void showSummary(MonthlySummary summary) {
        rideCountText.setText(
                "Completed rides: " + summary.getRideCount());

        organizedText.setText(
                "Rides organized: " + summary.getOrganizedCount());

        joinedText.setText(
                "Rides joined: " + summary.getJoinedCount());

        totalCostText.setText(
                "Total ride costs: "
                        + dollars(summary.getTotalCostCents()));

        myShareText.setText(
                "My share: " + dollars(summary.getMyShareCents()));

        paidText.setText(
                "Paid: " + dollars(summary.getPaidCents()));

        unpaidText.setText(
                "Outstanding: " + dollars(summary.getUnpaidCents()));

        statusText.setText(summary.getRideCount() == 0
                ? "No activity! Please select another month."
                : "Your monthly summary is ready!");
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        statusText.setText("Unable to load monthly summary.");
    }

    private String dollars(long cents) {
        return String.format(
                Locale.US, "$%.2f", cents / 100.0);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (selectedMonth != null) {
            outState.putString(KEY_MONTH, selectedMonth.toString());
        }
    }

    @Override
    public void onDestroyView() {
        requestVersion++;

        monthText = null;
        rideCountText = null;
        organizedText = null;
        joinedText = null;
        totalCostText = null;
        myShareText = null;
        paidText = null;
        unpaidText = null;
        statusText = null;
        errorText = null;

        super.onDestroyView();
    }
}
