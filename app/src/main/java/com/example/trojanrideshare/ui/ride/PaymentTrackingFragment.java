package com.example.trojanrideshare.ui.ride;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.FirestoreRideRepository;
import com.example.trojanrideshare.data.RideRepository;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.CostService;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Clock;
import com.example.trojanrideshare.util.Constants;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentTrackingFragment extends Fragment {

    private TextView summaryText;
    private TextView errorText;
    private LinearLayout paymentsContainer;

    private RideRepository repository;
    private CostService costService;
    private String rideId;
    private boolean saving;

    public PaymentTrackingFragment() {
        super(R.layout.fragment_payment_tracking);
    }

    @Override
    public void onViewCreated(
            @NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        summaryText = view.findViewById(R.id.textPaymentSummary);
        errorText = view.findViewById(R.id.textPaymentError);
        paymentsContainer = view.findViewById(R.id.containerPayments);

        // Open Monthly Summary without changing the current ride.
        Button monthlySummaryButton =
                view.findViewById(R.id.buttonMonthlySummary);

        monthlySummaryButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_global_monthlySummary));

        Clock clock = System::currentTimeMillis;

        repository = new FirestoreRideRepository(
                FirebaseFirestore.getInstance());

        RideService rideService = new RideService(repository, clock);
        costService = new CostService(repository, rideService, clock);

        Bundle args = getArguments();
        rideId = args == null
                ? null : args.getString(Constants.ARG_RIDE_ID);

        if (rideId == null || rideId.trim().isEmpty()) {
            summaryText.setText("No payment details available.");
            showError("No ride was selected.");
            return;
        }

        loadPayments();
    }

    // Read the latest payment information from Firestore.
    private void loadPayments() {
        if (saving) return;

        View screen = getView();
        if (screen == null) return;

        summaryText.setText("Loading payments...");
        hideError();

        repository.getById(rideId, new Callback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (getView() != screen) return;

                if (ride == null
                        || ride.getActivityStatus() != ActivityStatus.COMPLETED
                        || ride.getCostShares() == null) {
                    paymentsContainer.removeAllViews();
                    summaryText.setText("No payments available.");
                    showError("This ride has not been completed.");
                    return;
                }

                renderPayments(ride);
            }

            @Override
            public void onError(Exception error) {
                if (getView() != screen) return;

                summaryText.setText("Could not load payments.");
                showError("Could not load payments: "
                        + error.getMessage());
            }
        });
    }

    // Display each participant's cost share and payment state.
    private void renderPayments(Ride ride) {
        paymentsContainer.removeAllViews();
        hideError();

        Map<String, String> names = new HashMap<>();

        if (ride.getParticipants() != null) {
            for (Participant participant : ride.getParticipants()) {
                if (participant != null
                        && participant.getUserId() != null) {
                    names.put(participant.getUserId(),
                            participant.getDisplayName());
                }
            }
        }

        long total = 0;
        long paid = 0;
        long outstanding = 0;

        for (Map.Entry<String, CostShare> entry
                : ride.getCostShares().entrySet()) {

            String riderId = entry.getKey();
            CostShare share = entry.getValue();

            if (share == null) {
                showError("A payment record is missing.");
                continue;
            }

            long amount = share.getAmountCents();
            total += amount;

            if (share.isPaid()) {
                paid += amount;
            } else {
                outstanding += amount;
            }

            String name = names.get(riderId);
            if (name == null || name.trim().isEmpty()) {
                name = riderId;
            }

            addPaymentRow(ride, riderId, name, share);
        }

        summaryText.setText(
                "Total: " + dollars(total)
                        + "\nPaid: " + dollars(paid)
                        + "\nOutstanding: " + dollars(outstanding));
    }

    // Create each rider's payment row and authorized buttons.
    private void addPaymentRow(
            Ride ride,
            String riderId,
            String name,
            CostShare share) {

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 20, 16, 20);

        TextView nameText = new TextView(requireContext());
        nameText.setText(name + "  •  "
                + dollars(share.getAmountCents()));
        nameText.setTextSize(17);
        nameText.setTextColor(0xFF35244F);
        row.addView(nameText);

        boolean overdue = costService.isPaymentOverdue(
                ride, riderId);

        TextView statusText = new TextView(requireContext());

        if (share.isPaid()) {
            statusText.setText("Paid");
            statusText.setTextColor(0xFF38845E);
        } else if (share.isFlagged()) {
            statusText.setText("Flagged - payment overdue");
            statusText.setTextColor(0xFFB3261E);
        } else if (overdue) {
            statusText.setText("Overdue - payment pending");
            statusText.setTextColor(0xFFB3261E);
        } else {
            statusText.setText("Unpaid");
            statusText.setTextColor(0xFF746985);
        }

        statusText.setPadding(0, 8, 0, 8);
        row.addView(statusText);

        String currentUserId = CurrentUser.id();

        // Each rider can mark only their own share as Paid.
        if (!share.isPaid()
                && currentUserId != null
                && currentUserId.equals(riderId)) {

            Button paidButton = new Button(requireContext());
            paidButton.setText("Mark as paid");

            paidButton.setOnClickListener(v -> {
                if (saving) return;

                saving = true;
                paidButton.setEnabled(false);
                recordPayment(riderId);
            });

            row.addView(paidButton);
        }

        // Only the organizer can flag an overdue, unpaid rider.
        if (!share.isPaid()
                && !share.isFlagged()
                && overdue
                && ride.isOrganizer(currentUserId)) {

            Button flagButton = new Button(requireContext());
            flagButton.setText("Flag overdue");

            flagButton.setOnClickListener(v -> {
                if (saving) return;

                saving = true;
                flagButton.setEnabled(false);
                flagPayment(riderId);
            });

            row.addView(flagButton);
        }

        paymentsContainer.addView(row);
    }

    // Save the signed-in rider's payment.
    private void recordPayment(String riderId) {
        View screen = getView();

        costService.markRiderPaid(
                rideId,
                CurrentUser.id(),
                riderId,
                new Callback<Ride>() {
                    @Override
                    public void onSuccess(Ride ride) {
                        if (getView() != screen) return;

                        saving = false;
                        loadPayments();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (getView() != screen) return;

                        saving = false;
                        showError("Could not record payment: "
                                + error.getMessage());
                        reloadAfterError();
                    }
                });
    }

    // Let the organizer flag an overdue payment.
    private void flagPayment(String riderId) {
        View screen = getView();

        costService.flagRider(
                rideId,
                CurrentUser.id(),
                riderId,
                new Callback<Ride>() {
                    @Override
                    public void onSuccess(Ride ride) {
                        if (getView() != screen) return;

                        saving = false;
                        loadPayments();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (getView() != screen) return;

                        saving = false;
                        showError("Could not flag payment: "
                                + error.getMessage());
                        reloadAfterError();
                    }
                });
    }

    // Refresh after an error without hiding its explanation.
    private void reloadAfterError() {
        View screen = getView();
        if (screen == null) return;

        String previousError = errorText.getText().toString();

        repository.getById(rideId, new Callback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                if (getView() != screen) return;

                if (ride != null
                        && ride.getActivityStatus() == ActivityStatus.COMPLETED
                        && ride.getCostShares() != null) {
                    renderPayments(ride);
                }

                showError(previousError);
            }

            @Override
            public void onError(Exception error) {
                if (getView() != screen) return;

                showError("Could not refresh payments: "
                        + error.getMessage());
            }
        });
    }

    private String dollars(long cents) {
        return String.format(
                Locale.US, "$%d.%02d", cents / 100, cents % 100);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        saving = false;
        summaryText = null;
        errorText = null;
        paymentsContainer = null;
        super.onDestroyView();
    }
}
