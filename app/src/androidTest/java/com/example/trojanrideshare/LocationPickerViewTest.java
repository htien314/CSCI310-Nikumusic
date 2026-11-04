package com.example.trojanrideshare;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.ui.common.LocationPickerView;
import com.example.trojanrideshare.util.Callback;
import com.google.android.material.textfield.TextInputLayout;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocationPickerViewTest {
    @Test public void slowLookupCannotOverwriteEditedText() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                LocationPickerView picker = new LocationPickerView(activity);
                activity.setContentView(picker);
                AtomicReference<Callback<Location>> pending = new AtomicReference<>();
                picker.setGateway((query, callback) -> pending.set(callback));
                TextInputLayout field = (TextInputLayout) picker.getChildAt(0);
                field.getEditText().setText("USC");
                picker.findMatches();
                field.getEditText().setText("LAX");
                pending.get().onSuccess(new Location("USC", 34.02, -118.28));
                assertEquals("LAX", picker.getText());
                assertNull(picker.getSelectedLocation());
            });
        }
    }

    @Test public void formResolveRejectsAnAnswerForAnEditedLocation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                LocationPickerView picker = new LocationPickerView(activity);
                activity.setContentView(picker);
                AtomicReference<Callback<Location>> pending = new AtomicReference<>();
                AtomicReference<Exception> failure = new AtomicReference<>();
                picker.setGateway((query, callback) -> pending.set(callback));
                TextInputLayout field = (TextInputLayout) picker.getChildAt(0);
                field.getEditText().setText("USC");
                picker.resolve(new Callback<Location>() {
                    @Override public void onSuccess(Location place) { fail("Stale location reached the form"); }
                    @Override public void onError(Exception error) { failure.set(error); }
                });
                field.getEditText().setText("LAX");
                pending.get().onSuccess(new Location("USC", 34.02, -118.28));
                assertNotNull(failure.get());
                assertEquals("LAX", picker.getText());
            });
        }
    }
}
