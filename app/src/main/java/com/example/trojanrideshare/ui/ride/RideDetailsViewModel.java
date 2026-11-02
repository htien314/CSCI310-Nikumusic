package com.example.trojanrideshare.ui.ride;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.trojanrideshare.data.FirestoreRideRepository;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.ui.common.UiState;
import com.example.trojanrideshare.util.Callback;
import com.example.trojanrideshare.util.Constants;
import com.example.trojanrideshare.util.SystemTimeClock;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/** Loads one ride and runs the organizer's status changes on it. */
public class RideDetailsViewModel extends ViewModel {

    private static final String GENERIC_ERROR = "Something went wrong. Please try again.";

    public static final ViewModelProvider.Factory FACTORY = new ViewModelProvider.Factory() {
        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new RideDetailsViewModel(new RideService(
                    new FirestoreRideRepository(FirebaseFirestore.getInstance()), new SystemTimeClock()));
        }
    };

    private final RideService rideService;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<Ride> ride = new MutableLiveData<>();
    private final MutableLiveData<String> actionError = new MutableLiveData<>();

    public RideDetailsViewModel(RideService rideService) {
        this.rideService = rideService;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    public LiveData<Ride> getRide() {
        return ride;
    }

    /** Why the last status change failed, or null if it didn't. */
    public LiveData<String> getActionError() {
        return actionError;
    }

    public void load(String rideId) {
        if (isLoading()) {
            return;
        }
        if (rideId == null) {
            state.setValue(UiState.error("Ride not found."));
            return;
        }
        state.setValue(UiState.loading());
        rideService.getRide(rideId, new Callback<Ride>() {
            @Override
            public void onSuccess(Ride loaded) {
                ride.setValue(loaded);
                state.setValue(UiState.success());
            }

            @Override
            public void onError(Exception e) {
                state.setValue(UiState.error(e.getMessage() != null ? e.getMessage() : GENERIC_ERROR));
            }
        });
    }

    /** The date and time are in Los Angeles time, as on the create-ride form. */
    public void markDelayed(String userId, LocalDate date, LocalTime time) {
        Timestamp newTime = new Timestamp(Date.from(date.atTime(time).atZone(Constants.DISPLAY_ZONE).toInstant()));
        runAction(rideId -> rideService.markDelayed(rideId, userId, newTime, actionCallback()));
    }

    public void markOnTime(String userId) {
        runAction(rideId -> rideService.markOnTime(rideId, userId, actionCallback()));
    }

    public void cancelRide(String userId) {
        runAction(rideId -> rideService.cancelRide(rideId, userId, actionCallback()));
    }

    private void runAction(RideAction action) {
        Ride current = ride.getValue();
        if (current == null || isLoading()) {
            return;
        }
        actionError.setValue(null);
        state.setValue(UiState.loading());
        action.run(current.getId());
    }

    private Callback<Ride> actionCallback() {
        return new Callback<Ride>() {
            @Override
            public void onSuccess(Ride updated) {
                ride.setValue(updated);
                state.setValue(UiState.success());
            }

            @Override
            public void onError(Exception e) {
                state.setValue(UiState.success());
                actionError.setValue(e.getMessage() != null ? e.getMessage() : GENERIC_ERROR);
            }
        };
    }

    private boolean isLoading() {
        return state.getValue() != null && state.getValue().isLoading();
    }

    private interface RideAction {
        void run(String rideId);
    }
}
