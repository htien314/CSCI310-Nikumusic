package com.example.trojanrideshare.data;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.util.Callback;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Looks places up with the device's location service, which needs no API key. */
public class GeocoderLocationGateway implements LocationGateway {

    private static final String TAG = "GeocoderLocationGateway";

    // Southern California is searched first, so "LAX" means the airport and not Lax, Switzerland.
    private static final double NEARBY_SOUTH = 32.5;
    private static final double NEARBY_WEST = -120.0;
    private static final double NEARBY_NORTH = 35.0;
    private static final double NEARBY_EAST = -116.0;

    private final Geocoder geocoder;
    private final Executor background = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public GeocoderLocationGateway(Context context) {
        geocoder = new Geocoder(context.getApplicationContext(), Locale.US);
    }

    @Override
    public void lookup(String query, Callback<Location> callback) {
        String search = query == null ? "" : query.trim();
        if (search.isEmpty()) {
            callback.onError(new IllegalArgumentException("Enter an origin and a destination."));
            return;
        }
        if (!Geocoder.isPresent()) {
            callback.onError(new IllegalStateException("Location search isn't available on this device."));
            return;
        }
        background.execute(() -> {
            try {
                Location location = findWithRetry(search);
                mainThread.post(() -> {
                    if (location != null) {
                        callback.onSuccess(location);
                    } else {
                        callback.onError(new NoSuchElementException("Couldn't find \"" + search + "\"."));
                    }
                });
            } catch (IOException e) {
                Log.w(TAG, "Location lookup failed", e);
                mainThread.post(() -> callback.onError(
                        new IOException("Couldn't search for locations. Check your connection and try again.")));
            }
        });
    }

    /** The location service sometimes fails briefly, so a failed lookup gets one more try. */
    private Location findWithRetry(String search) throws IOException {
        try {
            return find(search);
        } catch (IOException e) {
            Log.w(TAG, "Location lookup failed, trying again", e);
            return find(search);
        }
    }

    /** Blocks until the location service answers, so this must stay off the main thread. */
    @SuppressWarnings("deprecation")
    private Location find(String search) throws IOException {
        List<Address> matches = geocoder.getFromLocationName(search, 1,
                NEARBY_SOUTH, NEARBY_WEST, NEARBY_NORTH, NEARBY_EAST);
        if (matches == null || matches.isEmpty()) {
            matches = geocoder.getFromLocationName(search, 1);
        }
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        Address match = matches.get(0);
        if (!match.hasLatitude() || !match.hasLongitude()) {
            return null;
        }
        String address = match.getAddressLine(0);
        return new Location(address != null ? address : search, match.getLatitude(), match.getLongitude());
    }
}
