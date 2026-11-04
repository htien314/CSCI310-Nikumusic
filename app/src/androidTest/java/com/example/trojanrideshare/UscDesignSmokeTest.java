package com.example.trojanrideshare;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.EditText;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Device checks for layout inflation, account navigation and access to every search filter. */
@RunWith(AndroidJUnit4.class)
public class UscDesignSmokeTest {
    private MainActivity activity;
    private NavController nav;
    @Before public void start() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        main(() -> nav = ((NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment)).getNavController());
    }
    @After public void finish() { if (activity != null) main(activity::finish); }
    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void open(int destination) { main(() -> nav.navigate(destination)); }
    private void capture(String name) throws Exception {
        File folder = new File(activity.getExternalFilesDir(null), "design-review");
        assertTrue(folder.exists() || folder.mkdirs());
        final Bitmap[] bitmap = new Bitmap[1];
        main(() -> {
            View root = activity.getWindow().getDecorView();
            bitmap[0] = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap[0]));
        });
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(bitmap[0].compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap[0].recycle();
    }
    @Test public void allFeatureScreensInflateAndExportReviewImages() throws Exception {
        int[] destinations = { R.id.profileFragment, R.id.loginFragment, R.id.registerFragment,
                R.id.searchFragment, R.id.createRideFragment, R.id.myRidesFragment,
                R.id.historyFragment, R.id.monthlySummaryFragment };
        String[] names = { "profile", "login", "register", "search", "create", "rides", "history", "summary" };
        for (int index = 0; index < destinations.length; index++) {
            open(destinations[index]);
            assertNotNull(activity.findViewById(R.id.bottom_nav));
            capture(names[index]);
        }
    }
    @Test public void searchFiltersRemainReachableAndKeepEnteredRoute() {
        open(R.id.searchFragment);
        main(() -> {
            assertEquals(View.GONE, activity.findViewById(R.id.search_filter_panel).getVisibility());
            activity.findViewById(R.id.search_more_filters).performClick();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.search_filter_panel).getVisibility());
            assertTrue(activity.findViewById(R.id.date_button).isShown());
            assertTrue(activity.findViewById(R.id.earliest_button).isShown());
            assertTrue(activity.findViewById(R.id.latest_button).isShown());
            assertTrue(activity.findViewById(R.id.seats_spinner).isShown());
            assertTrue(activity.findViewById(R.id.sort_spinner).isShown());
            EditText radius = activity.findViewById(R.id.radius_input);
            radius.setText("12");
            activity.findViewById(R.id.search_more_filters).performClick();
            activity.findViewById(R.id.search_more_filters).performClick();
            assertEquals("12", radius.getText().toString());
        });
    }
    @Test public void headerBackKeepsLoginReachableFromRegistration() {
        open(R.id.loginFragment);
        main(() -> ((EditText) activity.findViewById(R.id.inputLoginEmail)).setText("owner-test@usc.edu"));
        open(R.id.registerFragment);
        main(() -> activity.findViewById(R.id.header_back).performClick());
        main(() -> {
            assertEquals(R.id.loginFragment, nav.getCurrentDestination().getId());
            assertEquals("owner-test@usc.edu", ((EditText) activity.findViewById(R.id.inputLoginEmail)).getText().toString());
        });
    }
}
