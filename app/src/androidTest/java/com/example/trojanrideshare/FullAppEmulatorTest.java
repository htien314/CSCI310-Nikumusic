package com.example.trojanrideshare;

import android.content.Intent;
import android.view.View;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TimePicker;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Full form/navigation test. Deliberately refuses to run against a live Firebase project. */
@RunWith(AndroidJUnit4.class)
public class FullAppEmulatorTest {
    private MainActivity activity;
    private FirebaseFirestore db;
    private String rideId;
    private final String password="LocalTest123!";
    private final String title="Device test airport ride";
    private final LocalDate departure=LocalDate.now(java.time.ZoneId.of("America/Los_Angeles")).plusDays(1);

    @Test public void multipleAccountsThroughRealAndroidScreens() throws Exception {
        Assume.assumeTrue("Requires an isolated demo-project emulator build", BuildConfig.DEBUG
                && BuildConfig.USE_FIREBASE_EMULATOR
                && FirebaseApp.getInstance().getOptions().getProjectId().startsWith("demo-"));
        FirebaseAuth.getInstance().signOut();
        db=FirebaseFirestore.getInstance();
        launch();
        register("Owner","6000000001");
        createRide(title);
        DocumentSnapshot created=Tasks.await(db.collection("rides").whereEqualTo("title",title).whereEqualTo("organizerId",FirebaseAuth.getInstance().getUid()).get(),30,TimeUnit.SECONDS).getDocuments().get(0);
        rideId=created.getId();
        assertTrue(((Number)((java.util.Map<?,?>)created.get("origin")).get("latitude")).doubleValue()>33);
        checkpoint("Owner registered and created a ride using real geocoding");
        logout();register("Alice","6000000002");requestRide();
        logout();register("Bob","6000000003");requestRide();
        logout();login("Owner");openMembership();tapText("Accept Alice");
        awaitRide("capacityStatus","FULL");
        logout();login("Bob");openMembership();waitText(containsString("Your request: rejected"));
        checkpoint("Last-seat acceptance and automatic rejection visible across accounts");
        logout();login("Alice");openMembership();tapText("Leave ride");tapDialog("Leave ride");awaitRide("capacityStatus","OPEN");
        requestFromMembership();
        logout();login("Owner");openMembership();tapText("Accept Alice");awaitRide("capacityStatus","FULL");
        fixture("depart");openRide();waitId(R.id.complete_ride_button);tap(R.id.complete_ride_button);
        fill(R.id.inputTotalCost,"10.01");waitEnabled(R.id.buttonCompleteRide);tap(R.id.buttonCompleteRide);
        awaitRide("activityStatus","COMPLETED");waitText(containsString("Total: $10.01"));
        fixture("overdue");openPayments();
        waitFor(()->onView(allOf(withText("Flag overdue"),withParent(hasDescendant(withText(startsWith("Alice")))))).check(matches(isEnabled())));
        onView(allOf(withText("Flag overdue"),withParent(hasDescendant(withText(startsWith("Alice")))))).perform(scrollTo(),click());
        checkpoint("Actual cost completion and organizer overdue flag succeeded through Android SDK");
        logout();login("Alice");waitText(containsString("Overdue payment flags: 1"));
        openPayments();waitText(containsString("Flagged - payment overdue"));tapText("Mark as paid");
        waitText(containsString("Paid: $5.00"));
        profile();waitText(containsString("No overdue payment flags."));
        tap(R.id.profileViewHistoryButton);waitText(containsString("Your assigned share: $5.00"));waitText(containsString("Paid"));
        navigate(R.id.action_global_monthlySummary,null);waitText(containsString("My share: $5.00"));
        checkpoint("Payment, profile, actual-share history and monthly summary agree");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // Restart from the launcher to exercise restored sign-in and screen navigation.
        launch();profile();waitProfile("Alice");openPayments();waitText(containsString("Paid: $5.00"));
        checkpoint("Account and payment data survived Activity restart");
        logout();login("Owner");createRide("Device test cancellation");
        rideId=Tasks.await(db.collection("rides").whereEqualTo("title","Device test cancellation").whereEqualTo("organizerId",FirebaseAuth.getInstance().getUid()).get(),30,TimeUnit.SECONDS).getDocuments().get(0).getId();
        logout();login("Bob");openMembership();requestFromMembership();
        logout();login("Owner");openRide();tap(R.id.cancel_button);tapDialog("Cancel ride");awaitRide("activityStatus","CANCELLED");
        logout();login("Bob");openMembership();waitText(containsString("Your request: rejected"));
        checkpoint("Cancellation closed the pending request across accounts");
    }
    @Test public void accountAndRequestsSurviveProcessRestart() throws Exception {
        Assume.assumeTrue("Requires an isolated demo-project emulator build", BuildConfig.DEBUG
                && BuildConfig.USE_FIREBASE_EMULATOR
                && FirebaseApp.getInstance().getOptions().getProjectId().startsWith("demo-"));
        waitFor(()->assertNotNull(FirebaseAuth.getInstance().getCurrentUser()));
        assertEquals("bob-device@usc.edu",FirebaseAuth.getInstance().getCurrentUser().getEmail());
        db=FirebaseFirestore.getInstance();launch();profile();waitProfile("Bob");
        java.util.List<DocumentSnapshot> requests=Tasks.await(db.collection("joinRequests")
                .whereEqualTo("userId",FirebaseAuth.getInstance().getUid()).get(),30,TimeUnit.SECONDS).getDocuments();
        assertEquals(2,requests.size());
        for(DocumentSnapshot request:requests)assertEquals("REJECTED",request.getString("status"));
        checkpoint("Sign-in and server request state survived a full app process restart");
    }

    private void launch() {
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void register(String name,String id) throws Exception {
        profile();waitId(R.id.profileRegisterButton);tap(R.id.profileRegisterButton);
        fill(R.id.inputFullName,name);fill(R.id.inputNickname,name);fill(R.id.inputEmail,name.toLowerCase()+"-device@usc.edu");
        fill(R.id.inputUscId,id);fill(R.id.inputGraduationYear,String.valueOf(departure.getYear()+1));fill(R.id.inputPassword,password);
        tap(R.id.buttonRegister);waitProfile(name);
    }
    private void logout() throws Exception { profile();waitId(R.id.profileLogoutButton);tap(R.id.profileLogoutButton);waitId(R.id.profileLoginButton); }
    private void login(String name) throws Exception {
        profile();tap(R.id.profileLoginButton);fill(R.id.inputLoginEmail,name.toLowerCase()+"-device@usc.edu");fill(R.id.inputLoginPassword,password);
        tap(R.id.buttonLogin);waitProfile(name);
    }
    private void profile() { tab(R.id.profileFragment); }
    private void tab(int id) { InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((com.google.android.material.bottomnavigation.BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(id)); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void createRide(String name) throws Exception {
        tab(R.id.createRideFragment);waitId(R.id.title_input);
        fill(R.id.title_input,name);fill(R.id.origin_input,"University of Southern California, Los Angeles");
        fill(R.id.destination_input,"Los Angeles International Airport, Los Angeles");fill(R.id.cost_input,"10.01");
        onView(withId(R.id.max_riders_spinner)).perform(change(v->((Spinner)v).setSelection(0)));
        tap(R.id.date_button);
        onView(isAssignableFrom(DatePicker.class)).perform(change(v->((DatePicker)v).updateDate(departure.getYear(),departure.getMonthValue()-1,departure.getDayOfMonth())));
        onView(withId(android.R.id.button1)).perform(click());tap(R.id.time_button);
        onView(isAssignableFrom(TimePicker.class)).perform(change(v->{((TimePicker)v).setHour(12);((TimePicker)v).setMinute(0);}));
        onView(withId(android.R.id.button1)).perform(click());tap(R.id.create_button);waitId(R.id.ride_title);
    }
    private void requestRide() throws Exception {
        tab(R.id.searchFragment);waitText(equalTo(title));tap(R.id.request_button);requestFromMembership();
    }
    private void requestFromMembership() throws Exception { waitText(equalTo("Request to join"));tapText("Request to join");tapDialog("Send request");waitText(containsString("Your request: pending")); }
    private void openMembership() { bundleNavigate(R.id.action_global_requestsInbox); }
    private void openRide() { bundleNavigate(R.id.action_global_rideDetails); }
    private void openPayments() { bundleNavigate(R.id.action_global_paymentTracking); }
    private void bundleNavigate(int action) { android.os.Bundle args=new android.os.Bundle();args.putString("rideId",rideId);navigate(action,args); }
    private void navigate(int action,android.os.Bundle args) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((NavHostFragment)activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().navigate(action,args));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void fill(int id,String value) { onView(withId(id)).perform(scrollTo(),replaceText(value)); }
    private void tap(int id) throws Exception { waitFor(()->onView(withId(id)).check(matches(allOf(withEffectiveVisibility(Visibility.VISIBLE),isEnabled())))); onView(withId(id)).perform(scrollTo(),click()); }
    private void tapText(String text) throws Exception { waitFor(()->onView(allOf(withText(text),isAssignableFrom(android.widget.Button.class))).check(matches(isEnabled()))); onView(allOf(withText(text),isAssignableFrom(android.widget.Button.class))).perform(scrollTo(),click()); }
    private void tapDialog(String text) { onView(allOf(withText(equalToIgnoringCase(text)),isAssignableFrom(android.widget.Button.class))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click()); }
    private void waitProfile(String name) throws Exception { waitFor(()->onView(withId(R.id.profileFullName)).check(matches(withText(name)))); }
    private void waitId(int id) throws Exception { waitFor(()->onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))); }
    private void waitEnabled(int id) throws Exception { waitFor(()->onView(withId(id)).check(matches(isEnabled()))); }
    private void waitEnabledText(String text) throws Exception { waitFor(()->onView(withText(text)).check(matches(isEnabled()))); }
    private void waitText(Matcher<String> text) throws Exception { waitFor(()->onView(withText(text)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))); }
    private void waitFor(Runnable assertion) throws Exception {
        long end=System.currentTimeMillis()+45000;Throwable last=null;
        do {try {assertion.run();return;} catch(AssertionError|RuntimeException e){last=e;} Thread.sleep(200);} while(System.currentTimeMillis()<end);
        throw new AssertionError("Timed out waiting for UI. Last failure: "+last,last);
    }
    private void awaitRide(String field,String value) throws Exception { waitFor(()->{try {assertEquals(value,Tasks.await(db.collection("rides").document(rideId).get(),10,TimeUnit.SECONDS).getString(field));}catch(Exception e){throw new RuntimeException(e);}}); }
    private void fixture(String action) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("http://10.0.2.2:8765/fixture").openConnection();
        connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
        byte[] body=new JSONObject().put("action",action).put("rideId",rideId).toString().getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(body);assertEquals(200,connection.getResponseCode());connection.disconnect();
    }
    private ViewAction change(java.util.function.Consumer<View> action) {
        return new ViewAction(){public Matcher<View> getConstraints(){return isAssignableFrom(View.class);}public String getDescription(){return "Choose a form value";}public void perform(UiController ui,View view){action.accept(view);ui.loopMainThreadUntilIdle();}};
    }
    private void checkpoint(String text) { android.os.Bundle status=new android.os.Bundle();status.putString("stream","DEVICE CHECKPOINT: "+text+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(0,status); }
}
