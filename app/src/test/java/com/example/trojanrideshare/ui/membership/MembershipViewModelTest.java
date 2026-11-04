package com.example.trojanrideshare.ui.membership;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.trojanrideshare.data.JoinRequestRepository;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.MembershipService;
import com.example.trojanrideshare.util.Callback;
import org.junit.Rule;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class MembershipViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private static class Fake implements JoinRequestRepository {
        int removed, writes;
        String action, uid;
        Callback<List<Ride>> rides;
        Callback<List<JoinRequest>> mine, inbox;
        Callback<Void> write;
        public Subscription observeRides(Callback<List<Ride>> callback) { rides=callback; return () -> removed++; }
        public Subscription observeMine(String userId, Callback<List<JoinRequest>> callback) { uid=userId; mine=callback; return () -> removed++; }
        public Subscription observeInbox(String rideId, Callback<List<JoinRequest>> callback) { inbox=callback; return () -> removed++; }
        public void mutate(String a, String rideId, String requestId, String message, Callback<Void> callback) {
            writes++; action=a; write=callback;
        }
    }
    @Test public void signedOutDoesNotReadOrWrite() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake);
        vm.start(null); vm.act("leave","ride",null,null);
        assertNull(fake.rides); assertEquals(0,fake.writes); assertTrue(vm.getNotice().getValue().contains("log in"));
    }
    @Test public void switchingIdentityDetachesListenersAndIgnoresOldResponses() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake);
        vm.start("alice"); Callback<List<Ride>> old=fake.rides;
        vm.watchInbox("ride"); vm.start("bob");
        old.onSuccess(Collections.singletonList(new Ride()));
        assertEquals(3,fake.removed); assertTrue(vm.getRides().getValue().isEmpty()); assertEquals("bob",fake.uid);
    }
    @Test public void busyPreventsDoubleSubmissionAndResetsAfterError() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake); vm.start("alice");
        vm.act("request","ride",null,""); vm.act("request","ride",null,"");
        assertEquals(1,fake.writes); assertTrue(vm.getBusy().getValue());
        fake.write.onError(new IllegalStateException("Full"));
        assertFalse(vm.getBusy().getValue()); assertEquals("Full",vm.getNotice().getValue());
    }
    @Test public void staleActionDoesNotShowSuccessToNewAccount() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake); vm.start("alice");
        vm.act("request","ride",null,""); Callback<Void> old=fake.write; vm.start("bob"); old.onSuccess(null);
        assertNull(vm.getNotice().getValue()); assertFalse(vm.getBusy().getValue());
    }
    @Test public void inboxSwitchDropsOldOrganizerResults() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake); vm.start("alice");
        vm.watchInbox("one"); Callback<List<JoinRequest>> old=fake.inbox; vm.watchInbox("two");
        old.onSuccess(Collections.singletonList(new JoinRequest()));
        assertTrue(vm.getInbox().getValue().isEmpty()); assertEquals(1,fake.removed);
    }
    @Test public void readsStopLoadingAfterBothSourcesArrive() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake); vm.start("alice");
        fake.rides.onSuccess(new ArrayList<>()); assertTrue(vm.getLoading().getValue());
        fake.mine.onSuccess(new ArrayList<>()); assertFalse(vm.getLoading().getValue());
    }
    @Test public void failedReadClearsStaleDataAndReportsError() {
        Fake fake=new Fake(); MembershipViewModel vm=new MembershipViewModel(fake); vm.start("alice");
        fake.rides.onSuccess(Collections.singletonList(new Ride())); fake.rides.onError(new IllegalStateException("Offline"));
        assertTrue(vm.getRides().getValue().isEmpty()); assertEquals("Offline",vm.getNotice().getValue());
    }
    @Test public void serviceRejectsInvalidInputBeforeCallingBackend() {
        Fake fake=new Fake(); MembershipService service=new MembershipService(fake);
        List<Exception> errors=new ArrayList<>(); Callback<Void> callback=new Callback<Void>() {
            public void onSuccess(Void unused) { fail("Expected validation error"); }
            public void onError(Exception e) { errors.add(e); }
        };
        service.request("ride",String.join("",Collections.nCopies(1001,"x")),callback);
        service.request("", "", callback); service.accept("ride",null,callback); service.leave("a/b",callback);
        assertEquals(4,errors.size()); assertEquals(0,fake.writes);
    }
}
