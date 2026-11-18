package com.example.trojanrideshare;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** BB16–BB20: production membership screens and transactions against isolated emulators.
 * Auth and fixture seeding are setup, not substitutes for the request/accept/reject UI actions.
 * This suite fails fast rather than silently skipping when pointed at a live Firebase project.
 */
@RunWith(AndroidJUnit4.class)
public class MembershipBlackBoxTest {
    private MainActivity activity;
    private FirebaseFirestore db;
    private JSONObject fixture;
    private String rideId, requesterId, requesterName, requestId;

    @Before public void requireIsolatedEmulators() {
        assertTrue("Run npm run test:membership:device; never use live demo accounts.",
                BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR
                && FirebaseApp.getInstance().getOptions().getProjectId().equals("demo-trojan-device")
                && BuildConfig.APPLICATION_ID.endsWith(".integrationtest"));
        db=FirebaseFirestore.getInstance();
    }
    @After public void finishScreen() {
        closeActivity();
        if(BuildConfig.USE_FIREBASE_EMULATOR) FirebaseAuth.getInstance().signOut();
    }

    @Test public void bb16_submitShowsPendingToRequesterAndOrganizerWithoutJoining() throws Exception {
        seed(16); login(false);
        Map<String,Object> before=ride().getData();
        tapText("Request to join");
        onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText("Pickup at Leavey, please."),closeSoftKeyboard());
        dialog("Send request");
        waitText("Your request: pending");
        assertRequest("PENDING");
        assertEquals(before,ride().getData());
        assertEquals(1,ownRequests().size());
        assertEquals("Pickup at Leavey, please.",request().getString("message"));
        onView(withText(requesterName)).check(doesNotExist());
        login(true);
        waitText("Organizer inbox"); waitText("Accept "+requesterName);
        waitText(requesterName+" ·");
        assertEquals("PENDING",request().getString("status"));
        assertEquals(before,ride().getData());
    }

    @Test public void bb17_existingPendingRequestPreventsDuplicateSubmission() throws Exception {
        seed(17); login(false);
        waitText("Your request: pending");
        Map<String,Object> before=request().getData();
        onView(withText("Request to join")).check(doesNotExist());
        tapId(R.id.membership_refresh);
        waitText("Your request: pending");
        onView(withText("Request to join")).check(doesNotExist());
        assertEquals(1,ownRequests().size());
        assertEquals(before,request().getData());
        assertEquals(1,participants().size());
    }

    @Test public void bb18_organizerApprovesAndSeatsDecreaseByOne() throws Exception {
        seed(18); login(true);
        waitText("1/3 riders"); waitText("Accept "+requesterName);
        tapText("Accept "+requesterName);
        awaitStatus("ACCEPTED"); waitText("2/3 riders");
        onView(withText(requesterName)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        List<?> members=participants();
        assertEquals(2,members.size());
        assertTrue(((List<?>)ride().get("participantIds")).contains(requesterId));
        assertEquals(1,((Number)ride().get("maxRiders")).intValue()-members.size());
        assertEquals("OPEN",ride().getString("capacityStatus"));
        login(false); waitText("Your request: accepted"); waitText("2/3 riders");
    }

    @Test public void bb19_rejectShowsOutcomeWithoutChangingMembershipOrSeats() throws Exception {
        seed(19); login(true);
        Map<String,Object> before=ride().getData();
        tapText("Reject "+requesterName); dialog("Reject request");
        awaitStatus("REJECTED");
        assertEquals(before,ride().getData());
        waitText("No pending requests for this ride.");
        login(false); waitText("Your request: rejected");
        waitText("Your previous request was rejected.");
        onView(withText("Request to join")).check(doesNotExist());
        onView(withText(requesterName)).check(doesNotExist());
        assertEquals(before,ride().getData());
    }

    @Test public void bb20_fullRideRefusesApprovalAndPreservesAllRecords() throws Exception {
        // A stale pending request on a full ride is intentionally seeded, because accepting
        // the last seat normally auto-rejects the other requests before this UI can arise.
        seed(20); login(true);
        waitText("2/2 riders");
        Map<String,Object> beforeRide=ride().getData(),beforeRequest=request().getData();
        tapText("Accept "+requesterName);
        waitText("This ride is already full.");
        assertEquals(beforeRide,ride().getData());
        assertEquals(beforeRequest,request().getData());
        assertRequest("PENDING");
        assertFalse(((List<?>)ride().get("participantIds")).contains(requesterId));
        assertEquals("FULL",ride().getString("capacityStatus"));
        waitText("2/2 riders"); waitText("Accept "+requesterName);
    }

    private void seed(int scenario) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("http://10.0.2.2:8766/seed").openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json");
        try {
            connection.getOutputStream().write(new JSONObject().put("scenario",scenario).toString().getBytes(StandardCharsets.UTF_8));
            assertEquals("The local fixture server must be running.",200,connection.getResponseCode());
            fixture=new JSONObject(new String(connection.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
        rideId=fixture.getString("rideId"); requesterId=fixture.getString("riderId");
        requesterName=fixture.getString("riderName"); requestId=rideId+"_"+requesterId;
    }
    private void login(boolean organizer) throws Exception {
        closeActivity(); FirebaseAuth auth=FirebaseAuth.getInstance(); auth.signOut();
        Tasks.await(auth.signInWithEmailAndPassword(fixture.getString(organizer?"ownerEmail":"riderEmail"),
                fixture.getString("password")),20,TimeUnit.SECONDS);
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Bundle args=new Bundle(); args.putString("rideId",rideId);
            ((NavHostFragment)activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                    .getNavController().navigate(R.id.action_global_requestsInbox,args);
        });
        waitText(fixture.getString("title"));
    }
    private void closeActivity() {
        if(activity!=null) {
            MainActivity old=activity; activity=null;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(old::finish);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }
    private DocumentSnapshot ride() throws Exception { return Tasks.await(db.collection("rides").document(rideId).get(Source.SERVER),15,TimeUnit.SECONDS); }
    private DocumentSnapshot request() throws Exception { return Tasks.await(db.collection("joinRequests").document(requestId).get(Source.SERVER),15,TimeUnit.SECONDS); }
    private List<?> participants() throws Exception { return (List<?>)ride().get("participants"); }
    private List<DocumentSnapshot> ownRequests() throws Exception {
        return Tasks.await(db.collection("joinRequests").whereEqualTo("userId",requesterId).get(Source.SERVER),15,TimeUnit.SECONDS).getDocuments();
    }
    private void assertRequest(String expected) throws Exception { assertEquals(expected,request().getString("status")); }
    private void awaitStatus(String expected) throws Exception {
        waitFor(()->{try { assertRequest(expected); } catch(Exception error) { throw new RuntimeException(error); }});
    }
    private void waitText(String text) throws Exception {
        waitFor(()->onView(withText(containsString(text))).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    }
    private void tapText(String text) throws Exception {
        waitFor(()->onView(allOf(withText(text),isAssignableFrom(Button.class))).check(matches(isEnabled())));
        onView(allOf(withText(text),isAssignableFrom(Button.class))).perform(scrollTo(),click());
    }
    private void tapId(int id) { onView(withId(id)).perform(click()); }
    private void dialog(String text) { onView(withText(equalToIgnoringCase(text))).inRoot(isDialog()).perform(click()); }
    private void waitFor(Runnable assertion) throws Exception {
        long end=System.currentTimeMillis()+20000; Throwable last=null;
        do {
            try { assertion.run(); return; }
            catch(AssertionError|RuntimeException error) { last=error; }
            Thread.sleep(100);
        } while(System.currentTimeMillis()<end);
        throw new AssertionError("Timed out waiting for membership state",last);
    }
}
