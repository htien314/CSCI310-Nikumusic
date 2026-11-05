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
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.app_toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (!navController.navigateUp()) com.example.trojanrideshare.ui.auth.AccountNavigation.showProfile(navController);
        });
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            int id = destination.getId();
            boolean topLevel = id == R.id.profileFragment || id == R.id.searchFragment
                    || id == R.id.createRideFragment || id == R.id.myRidesFragment;
            toolbar.setVisibility(topLevel ? android.view.View.GONE : android.view.View.VISIBLE);
            toolbar.setTitle(destination.getLabel());
        });
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.profileFragment) {
                com.example.trojanrideshare.ui.auth.AccountNavigation.showProfile(navController, true);
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.profileFragment) {
                com.example.trojanrideshare.ui.auth.AccountNavigation.showProfile(navController, true);
            } else {
                navController.popBackStack(item.getItemId(), false);
            }
        });
    }
}
