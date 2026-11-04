package com.example.trojanrideshare.ui.ride;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.trojanrideshare.data.FakeLocationGateway;
import com.example.trojanrideshare.data.FakeRideRepository;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.model.User;
import com.example.trojanrideshare.service.OrganizerIdentityProvider;
import com.example.trojanrideshare.service.RideService;
import com.example.trojanrideshare.util.Callback;
import org.junit.Rule;
import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.junit.Assert.*;

public class AuthenticatedRideCreationTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private final FakeRideRepository rides = new FakeRideRepository();
    private CreateRideViewModel model(OrganizerIdentityProvider identity) {
        CreateRideViewModel model = new CreateRideViewModel(new RideService(rides, () -> 0L),
                new FakeLocationGateway(), identity);
        model.setDate(LocalDate.of(2030, 1, 1)); model.setTime(LocalTime.NOON);
        return model;
    }
    private User student(String nickname) {
        return new User("student-uid", "Hailey Tien", nickname, "htien@usc.edu", "1234567890", 2030);
    }
    private void create(CreateRideViewModel model) {
        model.createRideAsCurrentUser("Airport", "USC", "LAX", 3, "10.00", "");
    }
    @Test public void rideUsesAuthenticatedProfileUidAndNickname() {
        CreateRideViewModel model = model(callback -> callback.onSuccess(student("Hailey")));
        create(model);
        Ride ride = rides.rides.get(model.getCreatedRideId().getValue());
        assertEquals("student-uid", ride.getOrganizerId());
        assertEquals("Hailey", ride.getOrganizerName());
        assertEquals("student-uid", ride.getParticipants().get(0).getUserId());
    }
    @Test public void omittedNicknameUsesFullName() {
        CreateRideViewModel model = model(callback -> callback.onSuccess(student(null))); create(model);
        assertEquals("Hailey Tien", rides.rides.get(model.getCreatedRideId().getValue()).getOrganizerName());
    }
    @Test public void failedIdentityNeverCreatesRide() {
        CreateRideViewModel model = model(callback -> callback.onError(new IllegalStateException("Please log in.")));
        create(model);
        assertTrue(rides.rides.isEmpty()); assertTrue(model.getState().getValue().isError());
    }
    @Test public void missingProfileNeverCreatesRide() {
        CreateRideViewModel model = model(callback -> callback.onSuccess(null)); create(model);
        assertTrue(rides.rides.isEmpty()); assertTrue(model.getState().getValue().isError());
    }
    @Test public void resolvingIdentityPreventsDuplicateSubmission() {
        class Deferred implements OrganizerIdentityProvider {
            int calls; Callback<User> pending;
            public void load(Callback<User> callback) { calls++; pending = callback; }
        }
        Deferred identity = new Deferred(); CreateRideViewModel model = model(identity);
        create(model); create(model); assertEquals(1, identity.calls);
        identity.pending.onSuccess(student("Hailey")); assertEquals(1, rides.rides.size());
    }
}
