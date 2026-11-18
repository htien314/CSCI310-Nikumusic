package com.example.trojanrideshare;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.trojanrideshare.data.JoinRequestRepository;
import com.example.trojanrideshare.model.*;
import com.example.trojanrideshare.service.MembershipTransition;
import com.example.trojanrideshare.ui.membership.MembershipViewModel;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.Timestamp;
import org.junit.Rule;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** WB16–WB20: real ViewModel -> service -> transition, with fake persistence.
 * The fake calls production MembershipTransition rather than duplicating its guards.
 */
public class MembershipWhiteBoxTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private static final long NOW = 1_800_000_000_000L;
    private static final String OWNER = "organizer", RIDER = "requester";
    private static Timestamp at(long ms) { return new Timestamp(new Date(ms)); }
    private static String id(String uid) { return MembershipTransition.requestId("ride", uid); }
    private static MembershipViewModel start(FakeRepository fake, String uid) {
        MembershipViewModel vm = new MembershipViewModel(fake); vm.start(uid); return vm;
    }

    @Test public void wb16_submitSavesOnePendingRequestWithoutJoining() {
        FakeRepository repo = new FakeRepository(3);
        List<String> members = new ArrayList<>(repo.ride.getParticipantIds());
        MembershipViewModel vm = start(repo, RIDER);
        vm.act("request", "ride", null, "  Please pick me up at Leavey.  ");
        assertEquals(1, repo.commits); assertEquals(1, repo.requests.size());
        JoinRequest saved = repo.requests.get(id(RIDER));
        assertEquals(JoinRequest.Status.PENDING, saved.getStatus());
        assertEquals("ride", saved.getRideId()); assertEquals(RIDER, saved.getUserId());
        assertEquals("Please pick me up at Leavey.", saved.getMessage());
        assertEquals(at(NOW), saved.getCreatedAt());
        assertEquals(members, repo.ride.getParticipantIds());
        assertEquals(2, repo.ride.getAvailableSeats());
        assertEquals(JoinRequest.Status.PENDING, vm.getMine().getValue().get(0).getStatus());
        vm.start(OWNER); vm.watchInbox("ride");
        assertEquals(1, vm.getInbox().getValue().size());
        assertEquals(JoinRequest.Status.PENDING, vm.getInbox().getValue().get(0).getStatus());
        assertFalse(vm.getBusy().getValue());
    }

    @Test public void wb17_pendingGuardDoesNotWriteAndNoRequestControlSucceeds() {
        FakeRepository guarded = new FakeRepository(3); guarded.pending(RIDER);
        Map<String,Object> before = guarded.snapshot();
        MembershipViewModel vm = start(guarded, RIDER);
        vm.act("request", "ride", null, "duplicate");
        assertEquals(1, guarded.attempts); assertEquals(0, guarded.commits);
        assertEquals(before, guarded.snapshot());
        assertEquals("You already have a pending request.", vm.getNotice().getValue());
        assertFalse(vm.getBusy().getValue());
        FakeRepository control = new FakeRepository(3);
        start(control, RIDER).act("request", "ride", null, "first request");
        assertEquals(1, control.commits);
        assertEquals(JoinRequest.Status.PENDING, control.requests.get(id(RIDER)).getStatus());
    }

    @Test public void wb18_onlyOrganizerCanApproveAndSeatsDecreaseByOne() {
        FakeRepository repo = new FakeRepository(3); repo.pending(RIDER);
        Map<String,Object> before = repo.snapshot();
        MembershipViewModel vm = start(repo, "outsider");
        vm.act("accept", "ride", id(RIDER), null);
        assertEquals("Only the current organizer can do this.", vm.getNotice().getValue());
        assertEquals(0, repo.commits); assertEquals(before, repo.snapshot());
        vm.start(OWNER); vm.watchInbox("ride");
        int seats = repo.ride.getAvailableSeats();
        vm.act("accept", "ride", id(RIDER), null);
        assertEquals(1, repo.commits);
        assertEquals(Arrays.asList(OWNER, RIDER), repo.ride.getParticipantIds());
        assertEquals(seats - 1, repo.ride.getAvailableSeats());
        assertEquals(CapacityStatus.OPEN, repo.ride.getCapacityStatus());
        assertEquals(JoinRequest.Status.ACCEPTED, repo.requests.get(id(RIDER)).getStatus());
        assertEquals(JoinRequest.Status.ACCEPTED, vm.getInbox().getValue().get(0).getStatus());
        assertFalse(vm.getBusy().getValue());
    }

    @Test public void wb19_rejectPreservesMembershipAndResolvedRequestsCannotResolveAgain() {
        FakeRepository repo = new FakeRepository(3); repo.pending(RIDER);
        List<String> members = new ArrayList<>(repo.ride.getParticipantIds());
        int seats = repo.ride.getAvailableSeats();
        MembershipViewModel vm = start(repo, OWNER); vm.watchInbox("ride");
        vm.act("reject", "ride", id(RIDER), null);
        assertEquals(1, repo.commits);
        assertEquals(JoinRequest.Status.REJECTED, repo.requests.get(id(RIDER)).getStatus());
        assertEquals(members, repo.ride.getParticipantIds());
        assertEquals(seats, repo.ride.getAvailableSeats());
        assertEquals(JoinRequest.Status.REJECTED, vm.getInbox().getValue().get(0).getStatus());
        Map<String,Object> after = repo.snapshot();
        for (String action : Arrays.asList("reject", "accept")) {
            vm.act(action, "ride", id(RIDER), null);
            assertEquals("This request is no longer pending.", vm.getNotice().getValue());
            assertEquals(1, repo.commits); assertEquals(after, repo.snapshot());
            assertFalse(vm.getBusy().getValue());
        }
    }

    @Test public void wb20_lastSeatSucceedsButFullRideApprovalChangesNoRecords() {
        FakeRepository oneSeat = new FakeRepository(2);
        oneSeat.pending(RIDER); oneSeat.pending("waiting");
        MembershipViewModel vm = start(oneSeat, OWNER);
        assertEquals(1, oneSeat.ride.getAvailableSeats());
        vm.act("accept", "ride", id(RIDER), null);
        assertEquals(0, oneSeat.ride.getAvailableSeats());
        assertEquals(CapacityStatus.FULL, oneSeat.ride.getCapacityStatus());
        assertEquals(JoinRequest.Status.ACCEPTED, oneSeat.requests.get(id(RIDER)).getStatus());
        assertEquals(JoinRequest.Status.REJECTED, oneSeat.requests.get(id("waiting")).getStatus());
        assertEquals("RIDE_FULL", oneSeat.requests.get(id("waiting")).getClosedReason());
        FakeRepository full = new FakeRepository(2);
        full.ride.getParticipants().add(new Participant("existing", "Existing", at(NOW - 1)));
        full.ride.syncParticipantIds(); full.ride.syncCapacityStatus();
        // Deliberately stale pending requests: normal last-seat approval auto-rejects them.
        full.pending(RIDER); full.pending("waiting");
        Map<String,Object> before = full.snapshot();
        MembershipViewModel blocked = start(full, OWNER);
        blocked.act("accept", "ride", id(RIDER), null);
        assertEquals("This ride is already full.", blocked.getNotice().getValue());
        assertEquals(0, full.commits); assertEquals(before, full.snapshot());
        assertEquals(JoinRequest.Status.PENDING, full.requests.get(id(RIDER)).getStatus());
        assertEquals(0, full.ride.getAvailableSeats()); assertFalse(blocked.getBusy().getValue());
    }

    private static final class FakeRepository implements JoinRequestRepository {
        final Ride ride;
        final Map<String,JoinRequest> requests = new LinkedHashMap<>();
        String uid;
        int attempts, commits;
        Callback<List<Ride>> rideListener;
        Callback<List<JoinRequest>> mineListener, inboxListener;
        FakeRepository(int capacity) {
            ride = Ride.createNew("Leavey to LAX", new Location("USC Leavey",34.0217,-118.2828),
                    new Location("LAX",33.9416,-118.4085),at(NOW + 36_000_000L),capacity,
                    1000,"USC pickup",OWNER,"Organizer",at(NOW - 1000));
            ride.setId("ride");
        }
        void pending(String user) {
            JoinRequest r = new JoinRequest(); r.setId(id(user)); r.setRideId("ride");
            r.setUserId(user); r.setDisplayName(user); r.setMessage("Please accept");
            r.setStatus(JoinRequest.Status.PENDING); r.setCreatedAt(at(NOW - 100));
            r.setUpdatedAt(at(NOW - 100)); requests.put(r.getId(),r);
        }
        public Subscription observeRides(Callback<List<Ride>> cb) {
            rideListener=cb; cb.onSuccess(Collections.singletonList(ride));
            return () -> { if(rideListener==cb) rideListener=null; };
        }
        public Subscription observeMine(String userId,Callback<List<JoinRequest>> cb) {
            uid=userId; mineListener=cb; cb.onSuccess(mine());
            return () -> { if(mineListener==cb) mineListener=null; };
        }
        public Subscription observeInbox(String rideId,Callback<List<JoinRequest>> cb) {
            inboxListener=cb; cb.onSuccess(new ArrayList<>(requests.values()));
            return () -> { if(inboxListener==cb) inboxListener=null; };
        }
        public void mutate(String action,String rideId,String target,String message,Callback<Void> cb) {
            attempts++;
            MembershipTransition.Result result;
            try { result=MembershipTransition.apply(ride,new ArrayList<>(requests.values()),
                    action,uid,target,message,uid,at(NOW)); }
            catch(Exception error) { cb.onError(error); return; }
            // Commit only after production validation returns successfully.
            if(result.rideChanged) {
                ride.setParticipants(result.ride.getParticipants()); ride.syncParticipantIds();
                ride.setOrganizerId(result.ride.getOrganizerId());
                ride.setActivityStatus(result.ride.getActivityStatus());
                ride.setCapacityStatus(result.ride.getCapacityStatus());
            }
            for(JoinRequest r:result.changedRequests) requests.put(r.getId(),r);
            commits++;
            if(rideListener!=null) rideListener.onSuccess(Collections.singletonList(ride));
            if(mineListener!=null) mineListener.onSuccess(mine());
            if(inboxListener!=null) inboxListener.onSuccess(new ArrayList<>(requests.values()));
            cb.onSuccess(null);
        }
        List<JoinRequest> mine() {
            List<JoinRequest> result=new ArrayList<>();
            for(JoinRequest r:requests.values()) if(uid.equals(r.getUserId())) result.add(r);
            return result;
        }
        Map<String,Object> snapshot() {
            Map<String,Object> state=new LinkedHashMap<>();
            state.put("owner",ride.getOrganizerId()); state.put("members",new ArrayList<>(ride.getParticipantIds()));
            List<Object> people=new ArrayList<>();
            for(Participant p:ride.getParticipants()) people.add(Arrays.asList(p.getUserId(),p.getDisplayName(),p.getAcceptedAt()));
            state.put("participants",people); state.put("capacity",ride.getCapacityStatus());
            state.put("status",ride.getActivityStatus()); state.put("departure",ride.getDepartureTime());
            state.put("title",ride.getTitle()); state.put("cost",ride.getExpectedCostCents());
            for(JoinRequest r:requests.values()) state.put(r.getId(),Arrays.asList(r.getRideId(),r.getUserId(),
                    r.getDisplayName(),r.getMessage(),r.getStatus(),r.getCreatedAt(),r.getUpdatedAt(),r.getClosedReason()));
            return state;
        }
    }
}
