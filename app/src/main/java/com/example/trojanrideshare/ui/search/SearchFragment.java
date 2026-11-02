package com.example.trojanrideshare.ui.search;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.service.SearchResult;
import com.example.trojanrideshare.ui.common.LoadingErrorView;
import com.example.trojanrideshare.util.Constants;
import com.example.trojanrideshare.util.MoneyFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Search screen: find rides near a pickup location and destination (UR-3.1 to UR-3.7). */
public class SearchFragment extends Fragment {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DEPARTURE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US);

    /** Same order as the labels in the sort spinner. */
    private static final RideSortOrder[] SORT_ORDERS = {
            RideSortOrder.PROXIMITY_THEN_TIME,
            RideSortOrder.PROXIMITY,
            RideSortOrder.EARLIEST_FIRST,
            RideSortOrder.LATEST_FIRST,
    };

    private SearchViewModel viewModel;
    private EditText pickupInput;
    private EditText destinationInput;
    private EditText radiusInput;
    private CheckBox openSeatsCheckbox;
    private TextView resultsSummary;
    private LinearLayout resultsList;

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, SearchViewModel.FACTORY).get(SearchViewModel.class);

        pickupInput = view.findViewById(R.id.pickup_input);
        destinationInput = view.findViewById(R.id.destination_input);
        radiusInput = view.findViewById(R.id.radius_input);
        openSeatsCheckbox = view.findViewById(R.id.open_seats_checkbox);
        resultsSummary = view.findViewById(R.id.results_summary);
        resultsList = view.findViewById(R.id.results_list);
        Button dateButton = view.findViewById(R.id.date_button);
        Button earliestButton = view.findViewById(R.id.earliest_button);
        Button latestButton = view.findViewById(R.id.latest_button);
        Button clearButton = view.findViewById(R.id.clear_filters_button);
        Button searchButton = view.findViewById(R.id.search_button);

        if (savedInstanceState == null && radiusInput.getText().length() == 0) {
            radiusInput.setText(formatNumber(SearchViewModel.DEFAULT_RADIUS_MILES));
        }
        setUpSortSpinner(view.findViewById(R.id.sort_spinner));

        dateButton.setOnClickListener(v -> showDatePicker());
        earliestButton.setOnClickListener(v -> showTimePicker(viewModel.getEarliestTime().getValue(),
                viewModel::setEarliestTime));
        latestButton.setOnClickListener(v -> showTimePicker(viewModel.getLatestTime().getValue(),
                viewModel::setLatestTime));
        clearButton.setOnClickListener(v -> viewModel.clearDateAndTime());
        searchButton.setOnClickListener(v -> submit());

        LoadingErrorView loadingError = new LoadingErrorView(view.findViewById(R.id.loading_error), this::submit);

        viewModel.getDate().observe(getViewLifecycleOwner(), date -> dateButton.setText(
                date != null ? date.format(DATE_FORMAT) : getString(R.string.search_any_date)));
        viewModel.getEarliestTime().observe(getViewLifecycleOwner(), time -> earliestButton.setText(
                time != null ? getString(R.string.search_earliest, time.format(TIME_FORMAT))
                        : getString(R.string.search_any_earliest)));
        viewModel.getLatestTime().observe(getViewLifecycleOwner(), time -> latestButton.setText(
                time != null ? getString(R.string.search_latest, time.format(TIME_FORMAT))
                        : getString(R.string.search_any_latest)));
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            loadingError.render(state);
            searchButton.setEnabled(!state.isLoading());
        });
        viewModel.getResults().observe(getViewLifecycleOwner(), this::showResults);
    }

    private void setUpSortSpinner(Spinner spinner) {
        String[] labels = {
                getString(R.string.search_sort_proximity_then_time),
                getString(R.string.search_sort_proximity),
                getString(R.string.search_sort_earliest),
                getString(R.string.search_sort_latest),
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        RideSortOrder current = viewModel.getSortOrder().getValue();
        for (int i = 0; i < SORT_ORDERS.length; i++) {
            if (SORT_ORDERS[i] == current) {
                spinner.setSelection(i, false);
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewModel.setSortOrder(SORT_ORDERS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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

    private void showTimePicker(@Nullable LocalTime current, Consumer<LocalTime> onPicked) {
        LocalTime shown = current != null ? current : LocalTime.of(12, 0);
        new TimePickerDialog(requireContext(),
                (picker, hour, minute) -> onPicked.accept(LocalTime.of(hour, minute)),
                shown.getHour(), shown.getMinute(), DateFormat.is24HourFormat(requireContext())).show();
    }

    private void submit() {
        viewModel.search(
                pickupInput.getText().toString(),
                destinationInput.getText().toString(),
                radiusInput.getText().toString(),
                openSeatsCheckbox.isChecked());
    }

    private void showResults(@Nullable List<SearchResult> results) {
        resultsList.removeAllViews();
        if (results == null) {
            resultsSummary.setVisibility(View.GONE);
            return;
        }
        resultsSummary.setVisibility(View.VISIBLE);
        resultsSummary.setText(results.isEmpty()
                ? getString(R.string.search_no_results)
                : getResources().getQuantityString(R.plurals.search_results_count, results.size(), results.size()));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (SearchResult result : results) {
            View item = inflater.inflate(R.layout.item_search_result, resultsList, false);
            bind(item, result);
            resultsList.addView(item);
        }
    }

    private void bind(View item, SearchResult result) {
        Ride ride = result.getRide();
        String departure = Instant.ofEpochMilli(result.getDepartureMillis())
                .atZone(Constants.DISPLAY_ZONE).format(DEPARTURE_FORMAT);
        String cost = MoneyFormatter.format(ride.getExpectedCostCents());
        int seats = ride.getAvailableSeats();

        ((TextView) item.findViewById(R.id.result_title)).setText(ride.getTitle());
        ((TextView) item.findViewById(R.id.result_departure)).setText(
                ride.getActivityStatus() == ActivityStatus.DELAYED
                        ? getString(R.string.search_result_departure_delayed, departure) : departure);
        ((TextView) item.findViewById(R.id.result_origin)).setText(getString(R.string.search_result_origin,
                ride.getOrigin().getAddress(), formatMiles(result.getOriginDistanceMiles())));
        ((TextView) item.findViewById(R.id.result_destination)).setText(getString(
                R.string.search_result_destination,
                ride.getDestination().getAddress(), formatMiles(result.getDestinationDistanceMiles())));
        ((TextView) item.findViewById(R.id.result_seats_cost)).setText(seats == 0
                ? getString(R.string.search_result_full, cost)
                : getResources().getQuantityString(R.plurals.search_result_seats, seats, seats, cost));

        item.setOnClickListener(v -> openRide(ride.getId()));
    }

    private void openRide(String rideId) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_RIDE_ID, rideId);
        NavHostFragment.findNavController(this).navigate(R.id.action_global_rideDetails, args);
    }

    private static String formatMiles(double miles) {
        return String.format(Locale.US, "%.1f mi", miles);
    }

    /** 5.0 shows as "5", 2.5 as "2.5". */
    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
