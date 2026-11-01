package com.example.trojanrideshare.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CapacityStatus;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.ui.common.LoadingErrorView;
import com.example.trojanrideshare.util.Constants;
import com.example.trojanrideshare.util.MoneyFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows a ride to anyone, plus status controls for its organizer (UR-2.5 to UR-2.8). */
public class RideDetailsFragment extends Fragment {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.US);

    private RideDetailsViewModel viewModel;
    private View content;
    private View organizerControls;
    private TextView titleText;
    private Button delayButton;
    private Button onTimeButton;
    private Button cancelButton;
    private Button completeRideButton; // Added for ride completion

    public RideDetailsFragment() {
        super(R.layout.fragment_ride_details);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, RideDetailsViewModel.FACTORY).get(RideDetailsViewModel.class);

        content = view.findViewById(R.id.ride_content);
        organizerControls = view.findViewById(R.id.organizer_controls);
        titleText = view.findViewById(R.id.ride_title);
        delayButton = view.findViewById(R.id.delay_button);
        onTimeButton = view.findViewById(R.id.on_time_button);
        cancelButton = view.findViewById(R.id.cancel_button);

        // Added for ride completion
        completeRideButton = view.findViewById(R.id.complete_ride_button);
        completeRideButton.setOnClickListener(v -> {
            Ride ride = viewModel.getRide().getValue();
            if (ride == null || !ride.isOrganizer(CurrentUser.id())
                    || ride.isFinished()
                    || ride.getCurrentDepartureTime() == null
                    || ride.getCurrentDepartureTime().toDate().getTime()
                    > System.currentTimeMillis()) {
                return;
            }

            Bundle args = new Bundle();
            args.putString(Constants.ARG_RIDE_ID, ride.getId());

            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_global_completeRide, args);
        });

        delayButton.setOnClickListener(v -> showDelayDatePicker());
        onTimeButton.setOnClickListener(v -> viewModel.markOnTime(CurrentUser.id()));
        cancelButton.setOnClickListener(v -> confirmCancel());

        LoadingErrorView loadingError = new LoadingErrorView(view.findViewById(R.id.loading_error), this::load);

        viewModel.getRide().observe(getViewLifecycleOwner(), ride -> {
            if (ride != null) {
                showRide(ride);
            }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            loadingError.render(state);
            setControlsEnabled(!state.isLoading());
        });
        TextView actionErrorText = view.findViewById(R.id.action_error);
        viewModel.getActionError().observe(getViewLifecycleOwner(), message -> {
            actionErrorText.setText(message);
            actionErrorText.setVisibility(message != null ? View.VISIBLE : View.GONE);
        });

        load();
    }

    private void load() {
        viewModel.load(getArguments() != null ? getArguments().getString(Constants.ARG_RIDE_ID) : null);
    }

    private void showRide(Ride ride) {
        content.setVisibility(View.VISIBLE);
        titleText.setText(ride.getTitle());

        setRow(R.id.row_activity, R.string.ride_details_activity, getString(activityLabel(ride.getActivityStatus())));
        setRow(R.id.row_capacity, R.string.ride_details_capacity, capacityText(ride));
        setRow(R.id.row_departure, R.string.ride_details_departure, formatTime(ride.getCurrentDepartureTime()));
        setRow(R.id.row_original_departure, R.string.ride_details_original_departure,
                ride.getDelayedDepartureTime() != null ? formatTime(ride.getDepartureTime()) : null);
        setRow(R.id.row_origin, R.string.ride_details_origin, ride.getOrigin().getAddress());
        setRow(R.id.row_destination, R.string.ride_details_destination, ride.getDestination().getAddress());
        setRow(R.id.row_cost, R.string.ride_details_cost, MoneyFormatter.format(ride.getExpectedCostCents()));
        setRow(R.id.row_organizer, R.string.ride_details_organizer, ride.getOrganizerName());
        setRow(R.id.row_riders, R.string.ride_details_riders, ridersText(ride));
        setRow(R.id.row_note, R.string.ride_details_note, ride.getNote());

        boolean canManage = ride.isOrganizer(CurrentUser.id()) && !ride.isFinished();
        boolean delayed = ride.getActivityStatus() == ActivityStatus.DELAYED;
        organizerControls.setVisibility(canManage ? View.VISIBLE : View.GONE);

        // Added for ride completion
        boolean departed = ride.getCurrentDepartureTime() != null
                && ride.getCurrentDepartureTime().toDate().getTime()
                <= System.currentTimeMillis();
        completeRideButton.setVisibility(
                canManage && departed ? View.VISIBLE : View.GONE);

        delayButton.setText(delayed ? R.string.ride_details_change_delay : R.string.ride_details_mark_delayed);
        onTimeButton.setVisibility(delayed ? View.VISIBLE : View.GONE);
    }

    /** Hides the row when there is no value to show. */
    private void setRow(int rowId, @StringRes int label, @Nullable String value) {
        View row = requireView().findViewById(rowId);
        row.setVisibility(value != null ? View.VISIBLE : View.GONE);
        if (value != null) {
            ((TextView) row.findViewById(R.id.detail_label)).setText(label);
            ((TextView) row.findViewById(R.id.detail_value)).setText(value);
        }
    }

    @StringRes
    private static int activityLabel(ActivityStatus status) {
        switch (status) {
            case DELAYED:
                return R.string.ride_status_delayed;
            case CANCELLED:
                return R.string.ride_status_cancelled;
            case COMPLETED:
                return R.string.ride_status_completed;
            default:
                return R.string.ride_status_on_time;
        }
    }

    private String capacityText(Ride ride) {
        int riders = ride.getParticipants().size();
        if (ride.getCapacityStatus() == CapacityStatus.NOT_APPLICABLE) {
            return getString(R.string.ride_capacity_not_applicable);
        }
        if (ride.getCapacityStatus() == CapacityStatus.FULL) {
            return getString(R.string.ride_capacity_full, riders, ride.getMaxRiders());
        }
        return getResources().getQuantityString(R.plurals.ride_capacity_open, ride.getAvailableSeats(),
                riders, ride.getMaxRiders(), ride.getAvailableSeats());
    }

    private static String ridersText(Ride ride) {
        List<String> names = new ArrayList<>();
        for (Participant participant : ride.getParticipants()) {
            names.add(participant.getDisplayName());
        }
        return String.join("\n", names);
    }

    private static String formatTime(Timestamp time) {
        return time.toDate().toInstant().atZone(Constants.DISPLAY_ZONE).format(DATE_TIME_FORMAT);
    }

    private void setControlsEnabled(boolean enabled) {
        delayButton.setEnabled(enabled);
        onTimeButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
        completeRideButton.setEnabled(enabled); // Added for ride completion
    }

    private ZonedDateTime currentDeparture() {
        return viewModel.getRide().getValue().getCurrentDepartureTime().toDate().toInstant()
                .atZone(Constants.DISPLAY_ZONE);
    }

    private void showDelayDatePicker() {
        ZonedDateTime shown = currentDeparture();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> showDelayTimePicker(LocalDate.of(year, month + 1, day)),
                shown.getYear(), shown.getMonthValue() - 1, shown.getDayOfMonth());
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 86_400_000L);
        dialog.show();
    }

    private void showDelayTimePicker(LocalDate date) {
        ZonedDateTime shown = currentDeparture();
        new TimePickerDialog(requireContext(),
                (picker, hour, minute) -> viewModel.markDelayed(CurrentUser.id(), date, LocalTime.of(hour, minute)),
                shown.getHour(), shown.getMinute(), DateFormat.is24HourFormat(requireContext())).show();
    }

    private void confirmCancel() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ride_details_cancel_title)
                .setMessage(R.string.ride_details_cancel_message)
                .setPositiveButton(R.string.ride_details_cancel_confirm,
                        (dialog, which) -> viewModel.cancelRide(CurrentUser.id()))
                .setNegativeButton(R.string.ride_details_cancel_keep, null)
                .show();
    }
}
