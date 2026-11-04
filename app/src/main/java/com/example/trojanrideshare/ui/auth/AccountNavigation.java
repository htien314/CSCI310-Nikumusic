package com.example.trojanrideshare.ui.auth;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import com.example.trojanrideshare.R;

/** Always return to the account root instead of restoring an old login/signup stack. */
public final class AccountNavigation {
    private AccountNavigation() { }
    public static void showProfile(NavController controller) {
        if (controller.popBackStack(R.id.profileFragment, false)) return;
        if (controller.getCurrentDestination() != null
                && controller.getCurrentDestination().getId() == R.id.profileFragment) return;
        controller.navigate(R.id.profileFragment, null, new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, false).setLaunchSingleTop(true).build());
    }
}
