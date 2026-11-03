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
import com.example.trojanrideshare.data.RideRepository;
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
import java.util.Objects;

/**
 * State for the search screen. Before a search it lists upcoming rides to browse; after a search it
 * shows the matches. Both lists update live as other users change rides.
 */
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
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final MutableLiveData<LocalDate> date = new MutableLiveData<>();
    private final MutableLiveData<LocalTime> earliestTime = new MutableLiveData<>();
    private final MutableLiveData<LocalTime> latestTime = new MutableLiveData<>();
    private final MutableLiveData<RideSortOrder> sortOrder =
            new MutableLiveData<>(RideSortOrder.PROXIMITY_THEN_TIME);

    @Nullable
    private RideRepository.Subscription subscription;
    // Each browse/search bumps this, so answers from an older request are ignored.
    private int generation;
    private boolean started;
    @Nullable
    private String sessionUserId;

    public SearchViewModel(SearchService searchService, LocationGateway locations) {
        this.searchService = searchService;
        this.locations = locations;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    /** Null until the first list arrives; an empty list means nothing to show. */
    public LiveData<List<SearchResult>> getResults() {
        return results;
    }

    /** False while browsing upcoming rides, true while showing search results. */
    public LiveData<Boolean> getSearching() {
        return searching;
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

    /** Re-sorts the search results already shown, without searching again. */
    public void setSortOrder(RideSortOrder order) {
        if (order == null || order == sortOrder.getValue()) {
            return;
        }
        sortOrder.setValue(order);
        List<SearchResult> current = results.getValue();
        if (Boolean.TRUE.equals(searching.getValue()) && current != null) {
            results.setValue(sorted(current));
        }
    }

    /** Keeps the current search for the same account; restarts after login, logout or account switching. */
    public void start(@Nullable String currentUserId) {
        if (!started || !Objects.equals(sessionUserId, currentUserId)) {
            started = true;
            sessionUserId = currentUserId;
            browse(currentUserId);
        }
    }

    /** Lists upcoming rides with open seats that other users posted, earliest first. */
    public void browse(@Nullable String currentUserId) {
        int request = beginRequest(false);
        subscription = searchService.watchUpcoming(currentUserId, resultsCallback(request));
    }

    /**
     * Searches. For each location, a place the user already picked is used as is; otherwise the typed
     * text is looked up and the best match is used.
     */
    public void search(@Nullable Location pickupChoice, String pickupText,
                       @Nullable Location destinationChoice, String destinationText,
                       String radiusText, int minSeats, @Nullable String currentUserId) {
        if ((pickupChoice == null && isBlank(pickupText)) || (destinationChoice == null && isBlank(destinationText))) {
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

        int request = beginRequest(true);
        resolve(pickupChoice, pickupText, request, pickup ->
                resolve(destinationChoice, destinationText, request, destination -> {
                    SearchCriteria criteria = new SearchCriteria.Builder(pickup, destination, radius)
                            .date(date.getValue())
                            .timeWindow(earliestTime.getValue(), latestTime.getValue())
                            .minSeats(minSeats)
                            .excludeRidesOf(currentUserId)
                            .sortOrder(sortOrder.getValue())
                            .build();
                    subscription = searchService.watch(criteria, resultsCallback(request));
                }));
    }

    @Override
    protected void onCleared() {
        generation++;
        stopListening();
    }

    /** Stops the old list, marks a new request and shows loading. Returns the request number. */
    private int beginRequest(boolean isSearch) {
        stopListening();
        generation++;
        searching.setValue(isSearch);
        results.setValue(null);
        state.setValue(UiState.loading());
        return generation;
    }

    private void stopListening() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private Callback<List<SearchResult>> resultsCallback(int request) {
        return new Callback<List<SearchResult>>() {
            @Override
            public void onSuccess(List<SearchResult> found) {
                if (request != generation) {
                    return;
                }
                // A live update re-filters with the original criteria, but the sort may have changed since.
                results.setValue(Boolean.TRUE.equals(searching.getValue()) ? sorted(found) : found);
                state.setValue(UiState.success());
            }

            @Override
            public void onError(Exception e) {
                if (request == generation) {
                    showError(e);
                }
            }
        };
    }

    private void resolve(@Nullable Location choice, String text, int request, LocationStep next) {
        if (choice != null) {
            next.run(choice);
            return;
        }
        locations.lookup(text, new Callback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (request == generation) {
                    next.run(location);
                }
            }

            @Override
            public void onError(Exception e) {
                if (request == generation) {
                    showError(e);
                }
            }
        });
    }

    private List<SearchResult> sorted(List<SearchResult> list) {
        List<SearchResult> copy = new ArrayList<>(list);
        SearchService.sort(copy, sortOrder.getValue());
        return copy;
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
