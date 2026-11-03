package com.example.trojanrideshare.ui.ride;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.ui.common.LoadingErrorView;
import com.example.trojanrideshare.util.Constants;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Form for posting a new ride (UR-2.1). */
public class CreateRideFragment extends Fragment {

    private static final int DEFAULT_MAX_RIDERS = 4;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private CreateRideViewModel viewModel;
    private EditText titleInput;
    private EditText originInput;
    private EditText destinationInput;
    private EditText costInput;
    private EditText noteInput;
    private Spinner maxRidersSpinner;

    public CreateRideFragment() {
        super(R.layout.fragment_create_ride);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, CreateRideViewModel.FACTORY).get(CreateRideViewModel.class);

        titleInput = view.findViewById(R.id.title_input);
        originInput = view.findViewById(R.id.origin_input);
        destinationInput = view.findViewById(R.id.destination_input);
        costInput = view.findViewById(R.id.cost_input);
        noteInput = view.findViewById(R.id.note_input);
        maxRidersSpinner = view.findViewById(R.id.max_riders_spinner);
        Button dateButton = view.findViewById(R.id.date_button);
        Button timeButton = view.findViewById(R.id.time_button);
        Button createButton = view.findViewById(R.id.create_button);

        setUpMaxRiders();

        dateButton.setOnClickListener(v -> showDatePicker());
        timeButton.setOnClickListener(v -> showTimePicker());
        createButton.setOnClickListener(v -> submit());

        LoadingErrorView loadingError = new LoadingErrorView(view.findViewById(R.id.loading_error), this::submit);

        viewModel.getDate().observe(getViewLifecycleOwner(), date -> dateButton.setText(
                date != null ? date.format(DATE_FORMAT) : getString(R.string.create_ride_choose_date)));
        viewModel.getTime().observe(getViewLifecycleOwner(), time -> timeButton.setText(
                time != null ? time.format(TIME_FORMAT) : getString(R.string.create_ride_choose_time)));
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            loadingError.render(state);
            createButton.setEnabled(!state.isLoading());
        });
        viewModel.getCreatedRideId().observe(getViewLifecycleOwner(), rideId -> {
            if (rideId != null) {
                openRide(rideId);
            }
        });
    }

    private void setUpMaxRiders() {
        List<Integer> options = new ArrayList<>();
        for (int riders = Constants.MIN_RIDERS; riders <= Constants.MAX_RIDERS; riders++) {
            options.add(riders);
        }
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxRidersSpinner.setAdapter(adapter);
        maxRidersSpinner.setSelection(options.indexOf(DEFAULT_MAX_RIDERS));
    }

    private void showDatePicker() {
        LocalDate current = viewModel.getDate().getValue();
        LocalDate shown = current != null ? current : ZonedDateTime.now(Constants.DISPLAY_ZONE).toLocalDate();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> viewModel.setDate(LocalDate.of(year, month + 1, day)),
                shown.getYear(), shown.getMonthValue() - 1, shown.getDayOfMonth());
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 86_400_000L);
        dialog.show();
    }

    private void showTimePicker() {
        LocalTime current = viewModel.getTime().getValue();
        LocalTime shown = current != null ? current : ZonedDateTime.now(Constants.DISPLAY_ZONE).toLocalTime();
        new TimePickerDialog(requireContext(),
                (picker, hour, minute) -> viewModel.setTime(LocalTime.of(hour, minute)),
                shown.getHour(), shown.getMinute(), DateFormat.is24HourFormat(requireContext())).show();
    }

    private void submit() {
        viewModel.createRide(
                titleInput.getText().toString(),
                originInput.getText().toString(),
                destinationInput.getText().toString(),
                (Integer) maxRidersSpinner.getSelectedItem(),
                costInput.getText().toString(),
                noteInput.getText().toString(),
                CurrentUser.id(),
                CurrentUser.displayName());
    }

    private void openRide(String rideId) {
        titleInput.setText(null);
        originInput.setText(null);
        destinationInput.setText(null);
        costInput.setText(null);
        noteInput.setText(null);
        maxRidersSpinner.setSelection(DEFAULT_MAX_RIDERS - Constants.MIN_RIDERS);
        viewModel.onRideOpened();
        Bundle args = new Bundle();
        args.putString(Constants.ARG_RIDE_ID, rideId);
        NavHostFragment.findNavController(this).navigate(R.id.action_global_rideDetails, args);
    }
}
