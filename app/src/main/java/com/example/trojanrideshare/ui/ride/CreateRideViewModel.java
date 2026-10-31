package com.example.trojanrideshare.ui.ride;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.example.trojanrideshare.data.FirestoreRideRepository;
import com.example.trojanrideshare.data.GeocoderLocationGateway;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.ui.common.UiState;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.example.trojanrideshare.util.MoneyParser;
import com.example.trojanrideshare.util.SystemTimeClock;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/** Holds the create-ride form's state and turns its input into a RideService call. */
public class CreateRideViewModel extends ViewModel {

    public static final ViewModelProvider.Factory FACTORY = new ViewModelProvider.Factory() {
        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
            RideService rideService = new RideService(
                    new FirestoreRideRepository(FirebaseFirestore.getInstance()), new SystemTimeClock());
            Application application = extras.get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY);
            return (T) new CreateRideViewModel(rideService, new GeocoderLocationGateway(application));
        }
    };

    private final RideService rideService;
    private final LocationGateway locations;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<String> createdRideId = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> date = new MutableLiveData<>();
    private final MutableLiveData<LocalTime> time = new MutableLiveData<>();

    public CreateRideViewModel(RideService rideService, LocationGateway locations) {
        this.rideService = rideService;
        this.locations = locations;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    /** Set to the new ride's ID once it is saved, until {@link #onRideOpened()} is called. */
    public LiveData<String> getCreatedRideId() {
        return createdRideId;
    }

    public LiveData<LocalDate> getDate() {
        return date;
    }

    public LiveData<LocalTime> getTime() {
        return time;
    }

    public void setDate(LocalDate value) {
        date.setValue(value);
        clearError();
    }

    public void setTime(LocalTime value) {
        time.setValue(value);
        clearError();
    }

    /** An old error such as "Choose a date and time." no longer applies once these change. */
    private void clearError() {
        if (state.getValue() != null && state.getValue().isError()) {
            state.setValue(UiState.idle());
        }
    }

    public void createRide(String title, String originQuery, String destinationQuery, int maxRiders,
                           String costText, String note, String organizerId, String organizerName) {
        if (state.getValue() != null && state.getValue().isLoading()) {
            return;
        }
        long costCents;
        try {
            costCents = MoneyParser.parsePositiveCents(costText);
        } catch (IllegalArgumentException e) {
            state.setValue(UiState.error(e.getMessage()));
            return;
        }
        if (date.getValue() == null || time.getValue() == null) {
            state.setValue(UiState.error("Choose a date and time."));
            return;
        }
        Timestamp departure = new Timestamp(Date.from(
                date.getValue().atTime(time.getValue()).atZone(Constants.DISPLAY_ZONE).toInstant()));
        String trimmedNote = note == null || note.trim().isEmpty() ? null : note.trim();

        state.setValue(UiState.loading());
        locations.lookup(originQuery, afterLookup(origin ->
                locations.lookup(destinationQuery, afterLookup(destination ->
                        rideService.createRide(title, origin, destination, departure, maxRiders, costCents,
                                trimmedNote, organizerId, organizerName, new Callback<Ride>() {
                                    @Override
                                    public void onSuccess(Ride ride) {
                                        state.setValue(UiState.success());
                                        createdRideId.setValue(ride.getId());
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        showError(e);
                                    }
                                })))));
    }

    /** Resets the form after the screen has opened the new ride. */
    public void onRideOpened() {
        createdRideId.setValue(null);
        date.setValue(null);
        time.setValue(null);
        state.setValue(UiState.idle());
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

    private interface LocationStep {
        void run(Location location);
    }
}
