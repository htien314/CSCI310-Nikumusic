package com.example.trojanrideshare;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

/**
 * Project 2.4 black-box tests BB11-BB15 (author: Nikhil, search and locations).
 *
 * Drives the real Search tab as a signed-in student and checks only what appears on screen.
 * Rides posted by another user are seeded through the local test harness (functions/test/device/run.cjs,
 * POST /search-fixture), so every run starts from the same data. Like FullAppEmulatorTest, this refuses
 * to run outside the isolated demo-project emulator build.
 *
 * Run: cd functions && TROJAN_TEST_LOCATIONS=fixture npm run test:device
 * Seeded coordinates are placed around USC (34.0224, -118.2851) and LAX (33.9416, -118.4085).
 */
@RunWith(AndroidJUnit4.class)
public class SearchLocationBlackBoxTest {

    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final String PICKUP = "University of Southern California, Los Angeles";
    private static final String DESTINATION = "Los Angeles International Airport, Los Angeles";
    private static final double USC_LAT = 34.0224, USC_LNG = -118.2851;
    private static final double LAX_LAT = 33.9416, LAX_LNG = -118.4085;
    private static final double PASADENA_LAT = 34.1478, PASADENA_LNG = -118.1445;

    private static final String SEARCHER_NAME = "Searcher";
    private static final String SEARCHER_EMAIL = "searcher-device@usc.edu";
    private static final String SEARCHER_USC_ID = "6000000004";
    private static final String PASSWORD = "LocalTest123!";

    private final LocalDate tomorrow = LocalDate.now(LA).plusDays(1);
    private MainActivity activity;
    private boolean harnessReady;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Requires an isolated demo-project emulator build", BuildConfig.DEBUG
                && BuildConfig.USE_FIREBASE_EMULATOR
                && FirebaseApp.getInstance().getOptions().getProjectId().startsWith("demo-"));
        harnessReady = true;
        resetSeededRides();
        signInAsSearcher();
        launch();
        tab(R.id.searchFragment);
        waitId(R.id.search_button);
    }

    @After
    public void tearDown() throws Exception {
        if (activity != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        if (harnessReady) resetSeededRides();
    }

    // ------------------------------------------------------------------ BB11

    /** BB11 (use case): another user's future ride with seats appears for a matching search and opens. */
    @Test
    public void bb11_findMatchingRideAndOpenDetails() throws Exception {
        seed(ride("bbsearch-11", "BB11 USC to LAX", north(USC_LAT, 0.4), USC_LNG, north(LAX_LAT, 0.4), LAX_LNG,
                tomorrowAt(10, 0), 4, 1));

        search(PICKUP, DESTINATION, "5");
        waitSearchFinished();
        waitText(containsString("BB11 USC to LAX"));
        waitText(containsString("3 seats left"));

        onView(withText("BB11 USC to LAX")).perform(scrollTo(), click());
        waitFor(() -> onView(withId(R.id.ride_title)).check(matches(withText("BB11 USC to LAX"))));
        // The organizer's name also appears in the participants row, so check the organizer row itself.
        waitFor(() -> onView(allOf(withId(R.id.detail_value), isDescendantOfA(withId(R.id.row_organizer))))
                .check(matches(withText("Search Seeder"))));
    }

    // ------------------------------------------------------------------ BB12

    /** BB12 (equivalence partitioning): only a ride with BOTH endpoints inside the 5-mile radius shows. */
    @Test
    public void bb12_bothRouteEndpointsMustMatch() throws Exception {
        seed(ride("bbsearch-12a", "BB12 both ends inside", north(USC_LAT, 1), USC_LNG, north(LAX_LAT, 1), LAX_LNG,
                tomorrowAt(10, 0), 4, 1),
             ride("bbsearch-12b", "BB12 destination outside", north(USC_LAT, 1), USC_LNG, PASADENA_LAT, PASADENA_LNG,
                tomorrowAt(10, 0), 4, 1));

        search(PICKUP, DESTINATION, "5");
        waitSearchFinished();
        waitText(containsString("BB12 both ends inside"));
        onView(withText("BB12 destination outside")).check(doesNotExist());
    }

    // ------------------------------------------------------------------ BB13

    /** BB13 (boundary analysis): radius 0 and 101 are rejected; 1 and 100 are accepted. */
    @Test
    public void bb13_searchRadiusBoundaries() throws Exception {
        seed(ride("bbsearch-13", "BB13 very close ride", north(USC_LAT, 0.1), USC_LNG, north(LAX_LAT, 0.1), LAX_LNG,
                tomorrowAt(10, 0), 4, 1));

        search(PICKUP, DESTINATION, "0");
        waitError("The radius has to be more than 0 miles.");

        search(PICKUP, DESTINATION, "101");
        waitError("The radius can be at most 100 miles.");

        search(PICKUP, DESTINATION, "1");
        waitText(containsString("BB13 very close ride"));
        onView(withId(R.id.error_message)).check(matches(withEffectiveVisibility(Visibility.GONE)));

        search(PICKUP, DESTINATION, "100");
        waitText(containsString("BB13 very close ride"));
        onView(withId(R.id.error_message)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }

    // ------------------------------------------------------------------ BB14

    /** BB14 (input partitions): 09:00-12:00 needing 1 seat shows the 10:00 ride (2 seats), not 11:00 (full). */
    @Test
    public void bb14_filterByTimeWindowAndOpenSeats() throws Exception {
        seed(ride("bbsearch-14a", "BB14 ten with seats", north(USC_LAT, 0.5), USC_LNG, north(LAX_LAT, 0.5), LAX_LNG,
                tomorrowAt(10, 0), 3, 1),
             ride("bbsearch-14b", "BB14 eleven full", north(USC_LAT, 0.5), USC_LNG, north(LAX_LAT, 0.5), LAX_LNG,
                tomorrowAt(11, 0), 2, 2));

        fillPicker(R.id.pickup_picker, PICKUP);
        fillPicker(R.id.destination_picker, DESTINATION);
        fill(R.id.radius_input, "5");
        tap(R.id.searchToggleFilters);
        pickTime(R.id.earliest_button, 9, 0);
        waitText(containsString("Earliest: 9:00"));
        pickTime(R.id.latest_button, 12, 0);
        waitText(containsString("Latest: 12:00"));
        onView(withId(R.id.seats_spinner)).perform(scrollTo(), change(v -> ((Spinner) v).setSelection(1)));
        onView(withId(R.id.seats_spinner)).check(matches(withSpinnerText("At least 1")));
        tap(R.id.search_button);

        waitSearchFinished();
        waitText(containsString("BB14 ten with seats"));
        onView(withText("BB14 eleven full")).check(doesNotExist());
    }

    // ------------------------------------------------------------------ BB15

    /** BB15 (use-case variants): earliest, latest and closest orderings show the rides in the expected order. */
    @Test
    public void bb15_sortSearchResults() throws Exception {
        // Earliest ride is farthest away; latest ride is closest, so time order and distance order differ.
        seed(ride("bbsearch-15a", "BB15 ten far", north(USC_LAT, 2.5), USC_LNG, north(LAX_LAT, 2.5), LAX_LNG,
                tomorrowAt(10, 0), 4, 1),
             ride("bbsearch-15b", "BB15 eleven middle", north(USC_LAT, 1.5), USC_LNG, north(LAX_LAT, 1.5), LAX_LNG,
                tomorrowAt(11, 0), 4, 1),
             ride("bbsearch-15c", "BB15 twelve close", north(USC_LAT, 0.3), USC_LNG, north(LAX_LAT, 0.3), LAX_LNG,
                tomorrowAt(12, 0), 4, 1));

        search(PICKUP, DESTINATION, "5");
        waitText(containsString("BB15 twelve close"));
        tap(R.id.searchToggleFilters);

        chooseSort(2); // Earliest first
        waitOrder("BB15 ten far", "BB15 eleven middle", "BB15 twelve close");

        chooseSort(3); // Latest first
        waitOrder("BB15 twelve close", "BB15 eleven middle", "BB15 ten far");

        chooseSort(1); // Closest
        waitOrder("BB15 twelve close", "BB15 eleven middle", "BB15 ten far");
    }

    // ------------------------------------------------------------ screen steps

    private void search(String pickup, String destination, String radius) throws Exception {
        fillPicker(R.id.pickup_picker, pickup);
        fillPicker(R.id.destination_picker, destination);
        fill(R.id.radius_input, radius);
        tap(R.id.search_button);
    }

    private void fillPicker(int pickerId, String text) {
        onView(allOf(isDescendantOfA(withId(pickerId)), isAssignableFrom(EditText.class)))
                .perform(scrollTo(), replaceText(text));
    }

    private void pickTime(int buttonId, int hour, int minute) throws Exception {
        tap(buttonId);
        onView(isAssignableFrom(TimePicker.class)).perform(change(v -> {
            ((TimePicker) v).setHour(hour);
            ((TimePicker) v).setMinute(minute);
        }));
        onView(withId(android.R.id.button1)).perform(click());
    }

    private void chooseSort(int position) {
        onView(withId(R.id.sort_spinner)).perform(scrollTo(), change(v -> ((Spinner) v).setSelection(position)));
    }

    /** Waits until the given titles appear in this order among the displayed results. */
    private void waitOrder(String... expected) throws Exception {
        List<String> want = Arrays.asList(expected);
        waitFor(() -> assertEquals(want, displayedTitles(want)));
    }

    private List<String> displayedTitles(List<String> keep) {
        List<String> titles = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LinearLayout list = activity.findViewById(R.id.results_list);
            for (int i = 0; i < list.getChildCount(); i++) {
                TextView title = list.getChildAt(i).findViewById(R.id.result_title);
                if (title != null && keep.contains(title.getText().toString())) titles.add(title.getText().toString());
            }
        });
        return titles;
    }

    private void waitSearchFinished() throws Exception {
        waitFor(() -> onView(withId(R.id.results_summary)).check(matches(allOf(
                withEffectiveVisibility(Visibility.VISIBLE),
                withText(anyOf(containsString("found"), containsString("No rides match")))))));
    }

    private void waitError(String message) throws Exception {
        waitFor(() -> onView(withId(R.id.error_message)).check(matches(allOf(
                withEffectiveVisibility(Visibility.VISIBLE), withText(message)))));
    }

    // --------------------------------------------------------- account setup

    /** Signs in the searcher (student B), registering through the real sign-up screen the first time. */
    private void signInAsSearcher() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && SEARCHER_EMAIL.equals(current.getEmail())) return;
        auth.signOut();
        try {
            Tasks.await(auth.signInWithEmailAndPassword(SEARCHER_EMAIL, PASSWORD), 30, TimeUnit.SECONDS);
        } catch (Exception notRegisteredYet) {
            launch();
            tab(R.id.profileFragment);
            tap(R.id.profileRegisterButton);
            fill(R.id.inputFullName, SEARCHER_NAME);
            fill(R.id.inputNickname, SEARCHER_NAME);
            fill(R.id.inputEmail, SEARCHER_EMAIL);
            fill(R.id.inputUscId, SEARCHER_USC_ID);
            fill(R.id.inputGraduationYear, String.valueOf(tomorrow.getYear() + 1));
            fill(R.id.inputPassword, PASSWORD);
            tap(R.id.buttonRegister);
            waitFor(() -> onView(withId(R.id.profileFullName)).check(matches(withText(SEARCHER_NAME))));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    // ----------------------------------------------------------- seeded data

    private long tomorrowAt(int hour, int minute) {
        return ZonedDateTime.of(tomorrow, LocalTime.of(hour, minute), LA).toInstant().toEpochMilli();
    }

    /** Latitude a given number of miles north of the starting latitude. */
    private static double north(double latitude, double miles) {
        return latitude + miles / 69.05;
    }

    /** A ride organized by another user ("Search Seeder"). riders includes the organizer. */
    private static JSONObject ride(String id, String title, double originLat, double originLng,
                                   double destLat, double destLng, long departureMillis,
                                   int maxRiders, int riders) throws Exception {
        return new JSONObject().put("id", id).put("title", title)
                .put("originAddress", "Seeded pickup").put("originLat", originLat).put("originLng", originLng)
                .put("destAddress", "Seeded drop-off").put("destLat", destLat).put("destLng", destLng)
                .put("departureMillis", departureMillis).put("maxRiders", maxRiders).put("riders", riders);
    }

    private void seed(JSONObject... rides) throws Exception {
        JSONArray list = new JSONArray();
        for (JSONObject ride : rides) list.put(ride);
        post(new JSONObject().put("action", "seed").put("rides", list));
    }

    private void resetSeededRides() throws Exception {
        post(new JSONObject().put("action", "reset"));
    }

    private void post(JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://10.0.2.2:8765/search-fixture").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("Search fixture request failed", 200, connection.getResponseCode());
        connection.disconnect();
    }

    // -------------------------------------------------- shared Espresso helpers

    private void launch() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void tab(int id) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                ((BottomNavigationView) activity.findViewById(R.id.bottom_nav)).setSelectedItemId(id));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void fill(int id, String value) {
        onView(withId(id)).perform(scrollTo(), replaceText(value));
    }

    private void tap(int id) throws Exception {
        waitFor(() -> onView(withId(id)).check(matches(allOf(withEffectiveVisibility(Visibility.VISIBLE), isEnabled()))));
        onView(withId(id)).perform(scrollTo(), click());
    }

    private void waitId(int id) throws Exception {
        waitFor(() -> onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    }

    private void waitText(Matcher<String> text) throws Exception {
        waitFor(() -> onView(withText(text)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
    }

    /** Polls for an observable result (Firestore and geocoding are asynchronous) instead of a fixed sleep. */
    private void waitFor(Runnable assertion) throws Exception {
        long end = System.currentTimeMillis() + 45000;
        Throwable last = null;
        do {
            try {
                assertion.run();
                return;
            } catch (AssertionError | RuntimeException e) {
                last = e;
            }
            Thread.sleep(200);
        } while (System.currentTimeMillis() < end);
        throw new AssertionError("Timed out waiting for UI. Last failure: " + last, last);
    }

    private static Matcher<View> withSpinnerText(String text) {
        return androidx.test.espresso.matcher.ViewMatchers.withSpinnerText(text);
    }

    private ViewAction change(java.util.function.Consumer<View> action) {
        return new ViewAction() {
            public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            public String getDescription() { return "Choose a form value"; }
            public void perform(UiController ui, View view) { action.accept(view); ui.loopMainThreadUntilIdle(); }
        };
    }
}
