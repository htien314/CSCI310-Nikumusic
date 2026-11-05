package com.example.trojanrideshare;

import android.app.Activity;
import android.content.Intent;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.trojanrideshare.ui.auth.AccountNavigation;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Device regressions for bug-log items 1 and 2. No account mutations or live writes. */
@RunWith(AndroidJUnit4.class)
public class AccountNavigationTest {
    private MainActivity activity;
    private NavController controller;
    @Before public void open() {
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity launched=InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        activity=(MainActivity)launched;
        onMain(()->controller=((NavHostFragment)activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController());
    }
    @After public void close() { if(activity!=null) onMain(activity::finish); }
    private void onMain(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void profileLoginSearchProfileAlwaysShowsProfileRoot() {
        onMain(()->controller.navigate(R.id.loginFragment));
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.searchFragment));
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.profileFragment));
        onMain(()->assertEquals(R.id.profileFragment,controller.getCurrentDestination().getId()));
    }
    @Test public void reselectingProfileFromLoginReturnsToProfile() {
        onMain(()->controller.navigate(R.id.loginFragment));
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.profileFragment));
        onMain(()->assertEquals(R.id.profileFragment,controller.getCurrentDestination().getId()));
    }
    @Test public void authenticationReturnRemovesLoginAndSignupFromBackStack() {
        onMain(()->controller.navigate(R.id.loginFragment));
        onMain(()->controller.navigate(R.id.registerFragment));
        onMain(()->AccountNavigation.showProfile(controller));
        onMain(()->{
            assertEquals(R.id.profileFragment,controller.getCurrentDestination().getId());
            assertFalse(controller.popBackStack(R.id.loginFragment,false));
            assertFalse(controller.popBackStack(R.id.registerFragment,false));
        });
    }
    @Test public void profileReturnPreservesSearchForm() {
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.searchFragment));
        onMain(()->((android.widget.EditText)activity.findViewById(R.id.radius_input)).setText("12.5"));
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.profileFragment));
        onMain(()->((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.searchFragment));
        onMain(()->assertEquals("12.5",((android.widget.EditText)activity.findViewById(R.id.radius_input)).getText().toString()));
    }

    @Test public void childScreensOfferAnAccessibleBackButton() {
        onMain(()->controller.navigate(R.id.loginFragment));
        onMain(()->{
            com.google.android.material.appbar.MaterialToolbar toolbar=activity.findViewById(R.id.app_toolbar);
            assertEquals(android.view.View.VISIBLE,toolbar.getVisibility());
            assertEquals("Go back",toolbar.getNavigationContentDescription());
            assertNotNull(toolbar.getNavigationIcon());
        });
    }
}
