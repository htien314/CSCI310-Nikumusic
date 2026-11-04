package com.example.trojanrideshare;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Single activity for the whole app. Every screen is a Fragment destination in
 * res/navigation/nav_graph.xml; the bottom bar switches between the main tabs.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Pad for the status bar; BottomNavigationView pads itself for the system nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }
        NavController navController = navHostFragment.getNavController();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.brand_header), true);
        findViewById(R.id.header_back).setOnClickListener(view -> navController.navigateUp());
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean root = id == R.id.searchFragment || id == R.id.createRideFragment
                    || id == R.id.myRidesFragment || id == R.id.profileFragment;
            findViewById(R.id.header_back).setVisibility(root ? android.view.View.GONE : android.view.View.VISIBLE);
        });
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.profileFragment) {
                com.example.trojanrideshare.ui.auth.AccountNavigation.showProfile(navController);
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.profileFragment) {
                com.example.trojanrideshare.ui.auth.AccountNavigation.showProfile(navController);
            } else {
                navController.popBackStack(item.getItemId(), false);
            }
        });

        // Android 17 protects local addresses, including the Firebase demo on 10.0.2.2.
        // Normal live builds do not need or request access to the local network.
        if (BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR
                && android.os.Build.VERSION.SDK_INT >= 37
                && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, 7101);
        }
    }
}
