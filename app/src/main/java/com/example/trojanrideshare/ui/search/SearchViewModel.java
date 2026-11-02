package com.example.trojanrideshare.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.example.trojanrideshare.data.FirestoreRideRepository;
import com.example.trojanrideshare.data.GeocoderLocationGateway;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.RideSortOrder;
import com.example.trojanrideshare.model.SearchCriteria;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.service.SearchResult;
import com.example.trojanrideshare.service.SearchService;
import com.example.trojanrideshare.ui.common.UiState;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.SystemTimeClock;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Holds the search form's filters and results, and turns the form into a SearchService call. */
public class SearchViewModel extends ViewModel {

    public static final double DEFAULT_RADIUS_MILES = 5;

    public static final ViewModelProvider.Factory FACTORY = new ViewModelProvider.Factory() {
        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
            SearchService searchService = new SearchService(
                    new FirestoreRideRepository(FirebaseFirestore.getInstance()), new SystemTimeClock());
            Application application = extras.get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY);
            return (T) new SearchViewModel(searchService, new GeocoderLocationGateway(application));
        }
    };

    private final SearchService searchService;
    private final LocationGateway locations;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<List<SearchResult>> results = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> date = new MutableLiveData<>();
    private final MutableLiveData<LocalTime> earliestTime = new MutableLiveData<>();
    private final MutableLiveData<LocalTime> latestTime = new MutableLiveData<>();
    private final MutableLiveData<RideSortOrder> sortOrder =
            new MutableLiveData<>(RideSortOrder.PROXIMITY_THEN_TIME);

    public SearchViewModel(SearchService searchService, LocationGateway locations) {
        this.searchService = searchService;
        this.locations = locations;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    /** Null until the first search finishes; an empty list means nothing matched. */
    public LiveData<List<SearchResult>> getResults() {
        return results;
    }

    public LiveData<LocalDate> getDate() {
        return date;
    }

    public LiveData<LocalTime> getEarliestTime() {
        return earliestTime;
    }

    public LiveData<LocalTime> getLatestTime() {
        return latestTime;
    }

    public LiveData<RideSortOrder> getSortOrder() {
        return sortOrder;
    }

    /** Null means any date. */
    public void setDate(@Nullable LocalDate value) {
        date.setValue(value);
    }

    public void setEarliestTime(@Nullable LocalTime value) {
        earliestTime.setValue(value);
    }

    public void setLatestTime(@Nullable LocalTime value) {
        latestTime.setValue(value);
    }

    public void clearDateAndTime() {
        date.setValue(null);
        earliestTime.setValue(null);
        latestTime.setValue(null);
    }

    /** Re-sorts the results already shown, without searching again. */
    public void setSortOrder(RideSortOrder order) {
        if (order == null || order == sortOrder.getValue()) {
            return;
        }
        sortOrder.setValue(order);
        List<SearchResult> current = results.getValue();
        if (current != null) {
            List<SearchResult> resorted = new ArrayList<>(current);
            SearchService.sort(resorted, order);
            results.setValue(resorted);
        }
    }

    public void search(String pickupQuery, String destinationQuery, String radiusText, boolean openSeatsOnly) {
        if (state.getValue() != null && state.getValue().isLoading()) {
            return;
        }
        if (isBlank(pickupQuery) || isBlank(destinationQuery)) {
            state.setValue(UiState.error("Enter a pickup location and a destination."));
            return;
        }
        double radius;
        try {
            radius = Double.parseDouble(radiusText.trim());
        } catch (NumberFormatException | NullPointerException e) {
            state.setValue(UiState.error("Enter a radius in miles."));
            return;
        }

        state.setValue(UiState.loading());
        locations.lookup(pickupQuery, afterLookup(pickup ->
                locations.lookup(destinationQuery, afterLookup(destination -> {
                    SearchCriteria criteria = new SearchCriteria(pickup, destination, radius,
                            date.getValue(), earliestTime.getValue(), latestTime.getValue(),
                            openSeatsOnly, sortOrder.getValue());
                    searchService.search(criteria, new Callback<List<SearchResult>>() {
                        @Override
                        public void onSuccess(List<SearchResult> found) {
                            results.setValue(found);
                            state.setValue(UiState.success());
                        }

                        @Override
                        public void onError(Exception e) {
                            showError(e);
                        }
                    });
                }))));
    }

    private Callback<Location> afterLookup(LocationStep next) {
        return new Callback<Location>() {
            @Override
            public void onSuccess(Location location) {
                next.run(location);
            }

            @Override
            public void onError(Exception e) {
                showError(e);
            }
        };
    }

    private void showError(Exception e) {
        state.setValue(UiState.error(e.getMessage() != null
                ? e.getMessage() : "Something went wrong. Please try again."));
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private interface LocationStep {
        void run(Location location);
    }
}
