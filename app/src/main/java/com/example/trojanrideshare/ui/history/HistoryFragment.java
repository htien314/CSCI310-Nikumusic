
package com.example.trojanrideshare.ui.history;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.HistoryRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryFragment extends Fragment {

    private final List<HistoryRideItem> allRides = new ArrayList<>();
    private final HistoryRepository repository = new HistoryRepository();

    private HistoryRideAdapter rideAdapter;
    private EditText search;
    private Spinner statusFilter;
    private Spinner sort;
    private TextView message;
    private ProgressBar loading;

    private boolean isLoading = false;
    private String loadError = null;
    private boolean loginRequired = false;

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        search = view.findViewById(R.id.historySearch);
        statusFilter = view.findViewById(R.id.historyStatusFilter);
        sort = view.findViewById(R.id.historySort);
        message = view.findViewById(R.id.historyMessage);
        loading = view.findViewById(R.id.historyLoading);

        RecyclerView rideList =
                view.findViewById(R.id.historyRideList);

        rideList.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        rideAdapter = new HistoryRideAdapter();
        rideList.setAdapter(rideAdapter);

        String[] statusOptions = {
                "All",
                "Completed",
                "Cancelled"
        };

        ArrayAdapter<String> statusAdapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        statusOptions
                );

        statusAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        statusFilter.setAdapter(statusAdapter);

        String[] sortOptions = {
                "Newest First",
                "Oldest First"
        };

        ArrayAdapter<String> sortAdapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        sortOptions
                );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        sort.setAdapter(sortAdapter);

        // Search whenever the user changes the text.
        search.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {

                updateRideList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Refresh when either dropdown changes.
        AdapterView.OnItemSelectedListener filterListener =
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View selectedView,
                            int position,
                            long id) {

                        updateRideList();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                };

        statusFilter.setOnItemSelectedListener(filterListener);
        sort.setOnItemSelectedListener(filterListener);

        // Load actual Firebase rides.
        loadHistory(view);
    }

    private void loadHistory(View view) {

        allRides.clear();
        loadError = null;
        loginRequired = false;

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {

            loginRequired = true;
            isLoading = false;

            loading.setVisibility(View.GONE);
            updateRideList();
            return;
        }

        String uid = currentUser.getUid();

        isLoading = true;

        loading.setVisibility(View.VISIBLE);
        message.setText("Loading ride history...");

        rideAdapter.setRides(new ArrayList<>());

        // This loads BOTH organized and joined rides.
        repository.getMyRides(
                uid,
                snapshots -> {

                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    FirebaseUser activeUser =
                            FirebaseAuth.getInstance().getCurrentUser();

                    if (activeUser == null ||
                            !uid.equals(activeUser.getUid())) {

                        allRides.clear();
                        isLoading = false;
                        loginRequired = true;

                        loading.setVisibility(View.GONE);
                        updateRideList();
                        return;
                    }

                    allRides.clear();

                    for (DocumentSnapshot document : snapshots) {

                        String status =
                                document.getString("activityStatus");

                        // Only show completed or cancelled rides.
                        if (!"COMPLETED".equals(status)
                                && !"CANCELLED".equals(status)) {
                            continue;
                        }

                        String title =
                                document.getString("title");

                        if (title == null || title.trim().isEmpty()) {
                            title = "Untitled Ride";
                        }

                        String origin =
                                getAddress(document, "origin");

                        String destination =
                                getAddress(document, "destination");

                        // Use delayed time when available.
                        Timestamp departure =
                                document.getTimestamp(
                                        "delayedDepartureTime"
                                );

                        if (departure == null) {
                            departure =
                                    document.getTimestamp(
                                            "departureTime"
                                    );
                        }

                        long departureMillis =
                                departure == null
                                        ? 0L
                                        : departure.toDate()
                                        .getTime();

                        Long cost =
                                document.getLong(
                                        "expectedCostCents"
                                );

                        allRides.add(
                                new HistoryRideItem(
                                        title,
                                        origin + " → " + destination,
                                        departureMillis,
                                        formatStatus(status),
                                        cost == null ? 0L : cost
                                )
                        );
                    }

                    isLoading = false;
                    loading.setVisibility(View.GONE);

                    updateRideList();
                },
                error -> {

                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    allRides.clear();

                    isLoading = false;
                    loginRequired = false;
                    loadError = "Could not load ride history: "
                            + error.getMessage();

                    loading.setVisibility(View.GONE);
                    updateRideList();
                }
        );
    }

    // Read the address from a Firestore location map.
    private String getAddress(
            DocumentSnapshot document,
            String fieldName) {

        Object value = document.get(fieldName);

        if (value instanceof Map) {

            Map<?, ?> location = (Map<?, ?>) value;

            Object address = location.get("address");

            if (address instanceof String) {
                return (String) address;
            }
        }

        return "Unknown location";
    }

    private String formatStatus(String status) {

        if ("COMPLETED".equals(status)) {
            return "Completed";
        }

        if ("CANCELLED".equals(status)) {
            return "Cancelled";
        }

        return status;
    }

    // Apply search, status filtering, and sorting.
    private void updateRideList() {

        if (rideAdapter == null) {
            return;
        }

        if (isLoading) {
            return;
        }

        if (loginRequired) {
            rideAdapter.setRides(new ArrayList<>());
            message.setText(
                    "Please log in to view your ride history."
            );
            return;
        }

        if (loadError != null) {
            rideAdapter.setRides(new ArrayList<>());
            message.setText(loadError);
            return;
        }

        String query = search.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String selectedStatus =
                statusFilter.getSelectedItem() == null
                        ? "All"
                        : statusFilter.getSelectedItem()
                        .toString();

        String selectedSort =
                sort.getSelectedItem() == null
                        ? "Newest First"
                        : sort.getSelectedItem()
                        .toString();

        List<HistoryRideItem> filtered =
                new ArrayList<>();

        for (HistoryRideItem ride : allRides) {

            boolean matchesSearch =
                    ride.getTitle()
                            .toLowerCase(Locale.ROOT)
                            .contains(query)
                            ||
                            ride.getRoute()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(query);

            boolean matchesStatus =
                    selectedStatus.equals("All")
                            ||
                            ride.getStatus()
                                    .equalsIgnoreCase(selectedStatus);

            if (matchesSearch && matchesStatus) {
                filtered.add(ride);
            }
        }

        // Sort rides by their departure date.
        filtered.sort(
                Comparator.comparingLong(
                        HistoryRideItem::getDepartureMillis
                )
        );

        if (selectedSort.equals("Newest First")) {
            Collections.reverse(filtered);
        }

        rideAdapter.setRides(filtered);

        if (filtered.isEmpty()) {

            if (allRides.isEmpty()) {
                message.setText(
                        "No completed or cancelled rides yet."
                );
            } else {
                message.setText(
                        "No matching rides found."
                );
            }

        } else {

            message.setText(
                    filtered.size() + " ride(s) shown"
            );
        }
    }
}
