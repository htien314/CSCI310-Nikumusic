package com.example.trojanrideshare.ui.auth;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import com.example.trojanrideshare.R;

/** The account root is always fresh; other tabs may retain their saved forms. */
public final class AccountNavigation {
    private AccountNavigation() {}
    public static void showProfile(NavController controller) { showProfile(controller, false); }
    public static void showProfile(NavController controller, boolean preserveCurrentTab) {
        controller.clearBackStack(R.id.profileFragment);
        controller.navigate(R.id.profileFragment, null, new NavOptions.Builder()
                .setPopUpTo(R.id.profileFragment, false, preserveCurrentTab)
                .setLaunchSingleTop(true).setRestoreState(false).build());
    }
}
