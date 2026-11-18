package com.example.trojanrideshare;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Adapter;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TimePicker;

import androidx.navigation.fragment.NavHostFragment;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.hamcrest.Matcher;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * BB06–BB10 (Ada, ride management): Espresso black-box tests of the Create Ride and Ride Details
 * screens. Inputs come from the requirements, and each test checks only what the user sees plus the
 * saved ride. They run against isolated local Auth/Firestore emulators with fixed USC/LAX locations;
 * start them with {@code npm run test:ride:device --prefix functions}. The suite refuses to run
 * against a live Firebase project.
 */
@RunWith(AndroidJUnit4.class)
public class RideManagementBlackBoxTest {
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a", Locale.US);
    private static final String USC = "University of Southern California, Los Angeles";
    private static final String LAX = "Los Angeles International Airport, Los Angeles";

    private MainActivity activity;
    private FirebaseFirestore db;
    private JSONObject fixture;

    @Before public void requireIsolatedEmulators() {
        assertTrue("Run npm run test:ride:device; never use live demo accounts.",
                BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR
                        && FirebaseApp.getInstance().getOptions().getProjectId().equals("demo-trojan-device")
                        && BuildConfig.APPLICATION_ID.endsWith(".integrationtest"));
        db = FirebaseFirestore.getInstance();
    }

    @After public void finishScreen() {
        closeActivity();
        if (BuildConfig.USE_FIREBASE_EMULATOR) FirebaseAuth.getInstance().signOut();
    }

    /** BB06: post a USC→LAX ride for tomorrow with 4 riders and $40, then check its details. */
    @Test public void bb06_createValidRideShowsItsDetails() throws Exception {
        seed(6); signIn();
        ZonedDateTime departure = tomorrowAt(10, 0);
        String title = "BB06 USC to LAX " + fixture.getString("suffix");

        createRide(title, 4, "40", departure);

        waitText(title);
        assertRow(R.id.row_activity, "On-Time");
        assertRow(R.id.row_capacity, "Open: 1 of 4 riders, 3 seats left");
        assertRow(R.id.row_departure, SHOWN.format(departure));
        assertRow(R.id.row_origin, USC);
        assertRow(R.id.row_destination, LAX);
        assertRow(R.id.row_cost, "$40.00");
        assertRow(R.id.row_organizer, fixture.getString("name"));
        assertRow(R.id.row_riders, fixture.getString("name"));
        visible(R.id.delay_button); visible(R.id.cancel_button);

        DocumentSnapshot saved = onlyRideTitled(title);
        assertEquals(4000L, saved.getLong("expectedCostCents").longValue());
        assertEquals(4L, saved.getLong("maxRiders").longValue());
        assertEquals(fixture.getString("uid"), saved.getString("organizerId"));
        assertEquals(Collections.singletonList(fixture.getString("uid")), saved.get("participantIds"));
        assertEquals("ON_TIME", saved.getString("activityStatus"));
        assertEquals("OPEN", saved.getString("capacityStatus"));
        assertEquals(departure.toInstant().toEpochMilli(), saved.getTimestamp("departureTime").toDate().getTime());
    }

    /** BB07: the rider picker offers only 2–6 (1 and 7 can't be chosen); 2 and 6 both save. */
    @Test public void bb07_capacityPickerOffersOnlyTwoToSixAndBothEndsSave() throws Exception {
        seed(7); signIn(); tab(R.id.createRideFragment); waitId(R.id.max_riders_spinner);
        List<Integer> offered = new ArrayList<>();
        onView(withId(R.id.max_riders_spinner)).perform(change(v -> {
            Adapter adapter = ((Spinner) v).getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) offered.add((Integer) adapter.getItem(i));
        }));
        assertEquals(Arrays.asList(2, 3, 4, 5, 6), offered);

        String suffix = fixture.getString("suffix");
        createRide("BB07 two riders " + suffix, 2, "40", tomorrowAt(11, 0));
        waitText("BB07 two riders " + suffix);
        assertRow(R.id.row_capacity, "Open: 1 of 2 riders, 1 seat left");
        assertEquals(2L, onlyRideTitled("BB07 two riders " + suffix).getLong("maxRiders").longValue());

        createRide("BB07 six riders " + suffix, 6, "40", tomorrowAt(11, 0));
        waitText("BB07 six riders " + suffix);
        assertRow(R.id.row_capacity, "Open: 1 of 6 riders, 5 seats left");
        assertEquals(6L, onlyRideTitled("BB07 six riders " + suffix).getLong("maxRiders").longValue());
    }

    /** BB08: a ride for yesterday is refused with an error, and nothing is saved. */
    @Test public void bb08_pastDepartureIsRejectedAndNothingIsSaved() throws Exception {
        seed(8); signIn();
        String title = "BB08 yesterday " + fixture.getString("suffix");
        ZonedDateTime yesterday = ZonedDateTime.now(LA).minusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0);

        createRide(title, 4, "40", yesterday);

        waitError("Departure time must be in the future.");
        onView(withId(R.id.date_button)).check(matches(withText(
                yesterday.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)))));
        onView(withId(R.id.title_input)).check(matches(withText(title)));
        assertTrue("A past ride must not be saved.", ridesTitled(title).isEmpty());
    }

    /** BB09: the organizer delays tomorrow's ride by 30 minutes; the original time is kept. */
    @Test public void bb09_organizerDelaysRideAndOriginalTimeIsKept() throws Exception {
        seed(9); signIn();
        long originalMillis = fixture.getLong("departureMillis");
        ZonedDateTime original = Instant.ofEpochMilli(originalMillis).atZone(LA);
        ZonedDateTime later = original.plusMinutes(30);
        openRide(fixture.getString("rideId"));
        assertRow(R.id.row_activity, "On-Time");

        chooseDelay(later);

        waitRow(R.id.row_activity, "Delayed");
        assertRow(R.id.row_departure, SHOWN.format(later));
        assertRow(R.id.row_original_departure, SHOWN.format(original));
        onView(withId(R.id.delay_button)).check(matches(withText("Change delayed time")));
        DocumentSnapshot ride = ride(fixture.getString("rideId"));
        assertEquals("DELAYED", ride.getString("activityStatus"));
        assertEquals(later.toInstant().toEpochMilli(), ride.getTimestamp("delayedDepartureTime").toDate().getTime());
        assertEquals(originalMillis, ride.getTimestamp("departureTime").toDate().getTime());

        // A "delay" back to the original time is refused and changes nothing.
        chooseDelay(original);
        waitActionError("New departure time must be after the original time and in the future.");
        assertRow(R.id.row_departure, SHOWN.format(later));
        assertEquals(later.toInstant().toEpochMilli(),
                ride(fixture.getString("rideId")).getTimestamp("delayedDepartureTime").toDate().getTime());
    }

    /** BB10: cancelling just over 3 hours ahead works; just under 3 hours is refused. */
    @Test public void bb10_cancellationAllowedBeforeCutoffAndRefusedInsideIt() throws Exception {
        seed(10); signIn();
        String allowed = fixture.getString("allowedRideId"), blocked = fixture.getString("blockedRideId");

        openRide(allowed);
        cancelThroughDialog();
        waitRow(R.id.row_activity, "Cancelled");
        assertRow(R.id.row_capacity, "N/A");
        onView(withId(R.id.organizer_controls)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        assertEquals("CANCELLED", ride(allowed).getString("activityStatus"));

        openRide(blocked);
        cancelThroughDialog();
        waitActionError("Cancellation requires at least three hours before the original departure.");
        assertRow(R.id.row_activity, "On-Time");
        assertEquals("ON_TIME", ride(blocked).getString("activityStatus"));
        assertNull(ride(blocked).get("completedAt"));
    }

    // ---- Steps a user takes ----

    private void createRide(String title, int maxRiders, String cost, ZonedDateTime departure) throws Exception {
        tab(R.id.createRideFragment); waitId(R.id.title_input);
        fill(R.id.title_input, title); fill(R.id.origin_input, "USC"); fill(R.id.destination_input, "LAX");
        fill(R.id.cost_input, cost);
        onView(withId(R.id.max_riders_spinner)).perform(scrollTo(), change(v -> ((Spinner) v).setSelection(maxRiders - 2)));
        onView(withId(R.id.date_button)).perform(scrollTo(), click());
        pickDate(departure.toLocalDate());
        onView(withId(R.id.time_button)).perform(scrollTo(), click());
        pickTime(departure);
        onView(withId(R.id.create_button)).perform(scrollTo(), click());
    }

    private void chooseDelay(ZonedDateTime when) throws Exception {
        waitId(R.id.delay_button);
        onView(withId(R.id.delay_button)).perform(scrollTo(), click());
        pickDate(when.toLocalDate());
        pickTime(when);
    }

    private void cancelThroughDialog() throws Exception {
        waitId(R.id.cancel_button);
        onView(withId(R.id.cancel_button)).perform(scrollTo(), click());
        onView(withText(equalToIgnoringCase("Cancel ride"))).inRoot(isDialog()).perform(click());
    }

    private void pickDate(LocalDate date) {
        onView(isAssignableFrom(DatePicker.class)).inRoot(isDialog()).perform(change(v ->
                ((DatePicker) v).updateDate(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth())));
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    }

    private void pickTime(ZonedDateTime time) {
        onView(isAssignableFrom(TimePicker.class)).inRoot(isDialog()).perform(change(v -> {
            ((TimePicker) v).setHour(time.getHour()); ((TimePicker) v).setMinute(time.getMinute());
        }));
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    }

    // ---- Fixtures and navigation ----

    private void seed(int scenario) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://10.0.2.2:8767/seed").openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try {
            connection.getOutputStream().write(new JSONObject().put("scenario", scenario).toString().getBytes(StandardCharsets.UTF_8));
            assertEquals("The local fixture server must be running.", 200, connection.getResponseCode());
            fixture = new JSONObject(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    private void signIn() throws Exception {
        closeActivity(); FirebaseAuth auth = FirebaseAuth.getInstance(); auth.signOut();
        Tasks.await(auth.signInWithEmailAndPassword(fixture.getString("email"), fixture.getString("password")), 20, TimeUnit.SECONDS);
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void openRide(String rideId) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Bundle args = new Bundle(); args.putString("rideId", rideId);
            ((NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                    .getNavController().navigate(R.id.action_global_rideDetails, args);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        waitId(R.id.ride_title);
    }

    private void tab(int id) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                ((BottomNavigationView) activity.findViewById(R.id.bottom_nav)).setSelectedItemId(id));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void closeActivity() {
        if (activity != null) {
            MainActivity old = activity; activity = null;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(old::finish);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    private static ZonedDateTime tomorrowAt(int hour, int minute) {
        return ZonedDateTime.now(LA).plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    // ---- Firestore reads (the saved result) ----

    private DocumentSnapshot ride(String id) throws Exception {
        return Tasks.await(db.collection("rides").document(id).get(Source.SERVER), 15, TimeUnit.SECONDS);
    }

    private List<DocumentSnapshot> ridesTitled(String title) throws Exception {
        return Tasks.await(db.collection("rides").whereEqualTo("title", title)
                .whereEqualTo("organizerId", fixture.getString("uid")).get(Source.SERVER), 15, TimeUnit.SECONDS).getDocuments();
    }

    private DocumentSnapshot onlyRideTitled(String title) throws Exception {
        List<DocumentSnapshot> found = ridesTitled(title);
        assertEquals("Exactly one ride should be saved for " + title, 1, found.size());
        return found.get(0);
    }

    // ---- What the user sees ----

    private static Matcher<View> rowValue(int rowId) {
        return allOf(withId(R.id.detail_value), isDescendantOfA(withId(rowId)));
    }

    private void assertRow(int rowId, String expected) throws Exception { waitRow(rowId, expected); }

    private void waitRow(int rowId, String expected) throws Exception {
        waitFor(() -> onView(rowValue(rowId)).check(matches(allOf(withText(expected),
                withEffectiveVisibility(Visibility.VISIBLE)))));
    }

    private void waitText(String text) throws Exception {
        waitFor(() -> onView(allOf(withId(R.id.ride_title), withText(text))).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    }

    private void waitError(String text) throws Exception {
        waitFor(() -> onView(withId(R.id.error_message)).check(matches(allOf(withText(text),
                withEffectiveVisibility(Visibility.VISIBLE)))));
    }

    private void waitActionError(String text) throws Exception {
        waitFor(() -> onView(withId(R.id.action_error)).check(matches(allOf(withText(text),
                withEffectiveVisibility(Visibility.VISIBLE)))));
    }

    private void waitId(int id) throws Exception {
        waitFor(() -> onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    }

    private void visible(int id) {
        onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
    }

    private void fill(int id, String value) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard());
    }

    private static ViewAction change(Consumer<View> action) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "Choose a form value"; }
            @Override public void perform(UiController ui, View view) { action.accept(view); ui.loopMainThreadUntilIdle(); }
        };
    }

    private void waitFor(Runnable assertion) throws Exception {
        long end = System.currentTimeMillis() + 30000; Throwable last = null;
        do {
            try { assertion.run(); return; }
            catch (AssertionError | RuntimeException error) { last = error; }
            Thread.sleep(150);
        } while (System.currentTimeMillis() < end);
        throw new AssertionError("Timed out waiting for the ride screen", last);
    }
}
