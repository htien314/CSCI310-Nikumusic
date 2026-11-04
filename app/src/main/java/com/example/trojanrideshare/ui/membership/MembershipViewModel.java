package com.example.trojanrideshare.ui.membership;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.trojanrideshare.data.JoinRequestRepository;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.MembershipService;
import com.example.trojanrideshare.util.Callback;
import java.util.ArrayList;
import java.util.List;

/** Listeners survive rotation and are detached when the screen is destroyed or identity changes. */
public final class MembershipViewModel extends ViewModel {
    private final JoinRequestRepository repository;
    private final MembershipService service;
    private final MutableLiveData<List<Ride>> rides = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<JoinRequest>> mine = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<JoinRequest>> inbox = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> notice = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final List<JoinRequestRepository.Subscription> subscriptions = new ArrayList<>();
    private JoinRequestRepository.Subscription inboxSubscription;
    private String userId, inboxRideId;
    private int generation;
    private int ready;
    public MembershipViewModel(JoinRequestRepository repository) {
        this.repository = repository; service = new MembershipService(repository);
    }
    public LiveData<List<Ride>> getRides() { return rides; }
    public LiveData<List<JoinRequest>> getMine() { return mine; }
    public LiveData<List<JoinRequest>> getInbox() { return inbox; }
    public LiveData<String> getNotice() { return notice; }
    public LiveData<Boolean> getBusy() { return busy; }
    public LiveData<Boolean> getLoading() { return loading; }
    public String getUserId() { return userId; }
    public void start(String uid) {
        stop(); userId = uid;
        rides.setValue(new ArrayList<>()); mine.setValue(new ArrayList<>()); inbox.setValue(new ArrayList<>());
        notice.setValue(null); busy.setValue(false); ready = 0;
        if (uid == null) { notice.setValue("Please log in to manage rides and requests."); loading.setValue(false); return; }
        loading.setValue(true);
        int epoch = generation;
        subscriptions.add(repository.observeRides(stream(rides, epoch)));
        subscriptions.add(repository.observeMine(uid, stream(mine, epoch)));
    }
    private <T> Callback<List<T>> stream(MutableLiveData<List<T>> destination, int epoch) {
        return new Callback<List<T>>() {
            private boolean first = true;
            public void onSuccess(List<T> value) {
                if (epoch != generation) return;
                destination.setValue(value);
                if (first) { first = false; if (++ready >= 2) loading.setValue(false); }
            }
            public void onError(Exception e) {
                if (epoch != generation) return;
                destination.setValue(new ArrayList<>()); loading.setValue(false); notice.setValue(message(e));
            }
        };
    }
    public void watchInbox(String rideId) {
        if (java.util.Objects.equals(rideId, inboxRideId)) return;
        if (inboxSubscription != null) inboxSubscription.remove();
        inboxSubscription = null; inboxRideId = rideId; inbox.setValue(new ArrayList<>());
        if (rideId == null) return;
        int epoch = generation;
        inboxSubscription = repository.observeInbox(rideId, new Callback<List<JoinRequest>>() {
            public void onSuccess(List<JoinRequest> value) {
                if (epoch == generation && rideId.equals(inboxRideId)) inbox.setValue(value);
            }
            public void onError(Exception e) {
                if (epoch == generation && rideId.equals(inboxRideId)) {
                    inbox.setValue(new ArrayList<>()); notice.setValue(message(e));
                }
            }
        });
    }
    public void act(String action, String rideId, String requestId, String text) {
        if (Boolean.TRUE.equals(busy.getValue()) || userId == null) return;
        busy.setValue(true); notice.setValue(null); int epoch = generation;
        Callback<Void> callback = new Callback<Void>() {
            public void onSuccess(Void value) {
                if (epoch != generation) return;
                busy.setValue(false); notice.setValue("Saved. Your ride information will refresh automatically.");
            }
            public void onError(Exception e) {
                if (epoch != generation) return;
                busy.setValue(false); notice.setValue(message(e));
            }
        };
        switch (action) {
            case "request": service.request(rideId, text, callback); break;
            case "cancel": service.cancel(rideId, callback); break;
            case "accept": service.accept(rideId, requestId, callback); break;
            case "reject": service.reject(rideId, requestId, callback); break;
            case "leave": service.leave(rideId, callback); break;
            default: busy.setValue(false); notice.setValue("Unknown action.");
        }
    }
    private static String message(Exception e) { return e.getMessage() == null ? "Please refresh and try again." : e.getMessage(); }
    private void stop() {
        generation++;
        for (JoinRequestRepository.Subscription subscription : subscriptions) subscription.remove();
        subscriptions.clear();
        if (inboxSubscription != null) inboxSubscription.remove();
        inboxSubscription = null; inboxRideId = null;
    }
    @Override protected void onCleared() { stop(); }
}
