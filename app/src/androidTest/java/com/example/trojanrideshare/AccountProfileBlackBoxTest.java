package com.example.trojanrideshare;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

/** Member 1 / Riya: BB01–BB05. All behavioral assertions use the real UI. */
@RunWith(AndroidJUnit4.class)
public class AccountProfileBlackBoxTest {
    private static final String PROJECT = "demo-trojan-account-profile";
    private static final String PASSWORD = "LocalTest123!";
    private static final String NAME = "Student One";
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);
    private MainActivity activity;
    private String email;
    private String uscId;

    @Before public void openSignedOutProfile() {
        // Fail before sign-out or mutations if someone selects a live build by mistake.
        assertTrue("Use scripts/test-account-profile.cjs with the isolated demo APK",
                BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR
                        && PROJECT.equals(FirebaseApp.getInstance().getOptions().getProjectId())
                        && BuildConfig.APPLICATION_ID.equals("com.example.trojanrideshare.accountprofiletest"));
        FirebaseAuth.getInstance().signOut();
        email = "account-" + UUID.randomUUID().toString() + "@usc.edu";
        uscId = "7" + String.format(java.util.Locale.ROOT, "%09d", IDS.incrementAndGet() % 1_000_000_000L);
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onView(withId(R.id.profileLoginButton)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
    }

    @After public void close() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            FirebaseAuth.getInstance().signOut();
        }
    }

    @Test public void bb01_validRegistrationShowsTheSignedInStudentProfile() {
        openRegistration();
        fillRegistration(email, uscId);
        tap(R.id.buttonRegister);
        waitText(R.id.profileFullName, NAME);
        onView(withId(R.id.profileEmail)).check(matches(withText("USC Email: " + email)));
        onView(withId(R.id.profileUscId)).check(matches(withText("USC ID: " + uscId)));
        onView(withId(R.id.profileGraduationYear)).check(matches(withText("Graduation Year: " + year())));
        onView(withId(R.id.profileNickname)).check(matches(withText("Traveler")));
    }

    @Test public void bb02_nonUscEmailShowsValidationAndKeepsRegistrationOpen() {
        openRegistration();
        fillRegistration("student@gmail.com", uscId);
        tap(R.id.buttonRegister);
        waitText(R.id.textRegistrationError, "Enter a valid @usc.edu email address.");
        onView(withId(R.id.buttonRegister)).check(matches(isEnabled()));
        onView(withId(R.id.inputEmail)).check(matches(withText("student@gmail.com")));
        // A retry with a valid address must still be able to register successfully.
        fill(R.id.inputEmail, email);
        tap(R.id.buttonRegister);
        waitText(R.id.profileFullName, NAME);
    }

    @Test public void bb03_uscIdRequiresTenDigitsAndBlocksAnEleventhDigit() {
        openRegistration();
        fillRegistration(email, uscId.substring(0, 9));
        tap(R.id.buttonRegister);
        waitText(R.id.textRegistrationError, "USC ID must contain exactly 10 digits.");
        fill(R.id.inputUscId, uscId + "9");
        onView(withId(R.id.inputUscId)).check(matches(withText(uscId)));
        tap(R.id.buttonRegister);
        waitText(R.id.profileFullName, NAME);
        onView(withId(R.id.profileUscId)).check(matches(withText("USC ID: " + uscId)));
    }

    @Test public void bb04_wrongPasswordShowsAnErrorWithoutSigningIn() {
        registerThroughUi();
        logout();
        tap(R.id.profileLoginButton);
        fill(R.id.inputLoginEmail, email);
        fill(R.id.inputLoginPassword, "WrongPassword123!");
        tap(R.id.buttonLogin);
        waitText(R.id.textLoginError, "Login failed. Check your email and password.");
        onView(withId(R.id.buttonLogin)).check(matches(isEnabled()));
        // Return using the visible toolbar to verify the signed-out profile remains.
        onView(withContentDescription("Go back")).perform(click());
        onView(withId(R.id.profileSignedOut)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.profileSignedIn)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        login();
        waitText(R.id.profileFullName, NAME);
    }

    @Test public void bb05_nicknameRejectsBlankAndPersistsAfterLoggingInAgain() {
        registerThroughUi();
        fill(R.id.profileNickname, "   ");
        tap(R.id.profileSaveNicknameButton);
        waitText(R.id.profileMessage, "Nickname cannot be empty.");
        fill(R.id.profileNickname, "Traveler One");
        tap(R.id.profileSaveNicknameButton);
        waitText(R.id.profileMessage, "Nickname updated successfully!");
        logout();
        login();
        waitText(R.id.profileFullName, NAME);
        onView(withId(R.id.profileNickname)).check(matches(withText("Traveler One")));
    }

    private String year() { return String.valueOf(Year.now(ZoneOffset.UTC).getValue() + 1); }
    private void openRegistration() { tap(R.id.profileRegisterButton); }
    private void fillRegistration(String address, String id) {
        fill(R.id.inputFullName, NAME);
        fill(R.id.inputNickname, "Traveler");
        fill(R.id.inputEmail, address);
        fill(R.id.inputUscId, id);
        fill(R.id.inputGraduationYear, year());
        fill(R.id.inputPassword, PASSWORD);
    }
    private void registerThroughUi() {
        openRegistration(); fillRegistration(email, uscId); tap(R.id.buttonRegister);
        waitText(R.id.profileFullName, NAME);
    }
    private void logout() {
        tap(R.id.profileLogoutButton);
        onView(withId(R.id.profileSignedOut)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
    }
    private void login() {
        tap(R.id.profileLoginButton);
        fill(R.id.inputLoginEmail, email); fill(R.id.inputLoginPassword, PASSWORD); tap(R.id.buttonLogin);
    }
    private void fill(int id, String text) {
        onView(withId(id)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }
    private void tap(int id) { onView(withId(id)).perform(scrollTo(), click()); }

    /** Poll a visible outcome while pumping the UI queue; no arbitrary Thread.sleep. */
    private void waitText(int id, String text) {
        onView(isRoot()).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isRoot(); }
            public String getDescription() { return "Wait for view " + id + " to show: " + text; }
            public void perform(UiController controller, View root) {
                long deadline = android.os.SystemClock.uptimeMillis() + 30_000;
                do {
                    View found = root.findViewById(id);
                    if (found instanceof TextView && text.contentEquals(((TextView) found).getText())
                            && found.getVisibility() == View.VISIBLE) return;
                    controller.loopMainThreadForAtLeast(50);
                } while (android.os.SystemClock.uptimeMillis() < deadline);
                throw new AssertionError(getDescription());
            }
        });
        onView(withId(id)).check(matches(allOf(withText(text), withEffectiveVisibility(Visibility.VISIBLE))));
    }
}
