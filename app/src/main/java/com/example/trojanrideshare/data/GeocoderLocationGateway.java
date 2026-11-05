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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LocationGateway backed by Android's Geocoder, which uses Google's geocoding service on devices
 * and emulators with Google Play / Google APIs. It needs no API key. When the Geocoder fails, times
 * out or finds nothing, OpenStreetMap's Nominatim is asked instead. See docs/nikhil-search-locations.md.
 */
public class GeocoderLocationGateway implements LocationGateway {

    private static final String TAG = "GeocoderLocationGateway";

    // Southern California is searched first, so "LAX" means the airport and not Lax, Switzerland.
    private static final double NEARBY_SOUTH = 32.5;
    private static final double NEARBY_WEST = -120.0;
    private static final double NEARBY_NORTH = 35.0;
    private static final double NEARBY_EAST = -116.0;

    private static final long GEOCODER_TIME_LIMIT_SECONDS = 8;

    // Shared by every screen's gateway: after one timeout, the rest of this app run uses OpenStreetMap.
    private static volatile boolean geocoderTimedOut;

    private final Geocoder geocoder;
    private final NominatimLocationGateway fallback = new NominatimLocationGateway();
    private final Executor background = Executors.newSingleThreadExecutor();
    // Geocoder calls run separately so a hung call can be abandoned after the time limit.
    private final ExecutorService geocoderCalls = Executors.newCachedThreadPool();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public GeocoderLocationGateway(Context context) {
        geocoder = new Geocoder(context.getApplicationContext(), Locale.US);
    }

    @Override
    public void lookup(String query, Callback<Location> callback) {
        suggest(query, 1, new Callback<List<Location>>() {
            @Override
            public void onSuccess(List<Location> result) {
                callback.onSuccess(result.get(0));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void suggest(String query, int maxResults, Callback<List<Location>> callback) {
        String search = query == null ? "" : query.trim();
        if (search.isEmpty()) {
            callback.onError(new IllegalArgumentException("Enter an origin and a destination."));
            return;
        }
        int max = Math.max(1, maxResults);
        background.execute(() -> {
            List<Location> found = new ArrayList<>();
            boolean geocoderFailed = false;
            if (Geocoder.isPresent() && !geocoderTimedOut) {
                try {
                    found = findWithTimeLimit(search, max);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Location lookup rejected", e);
                    mainThread.post(() -> callback.onError(
                            new IllegalArgumentException("Couldn't understand \"" + search + "\". Try a different place.")));
                    return;
                } catch (IOException | TimeoutException e) {
                    Log.w(TAG, "Geocoder lookup failed, using OpenStreetMap instead", e);
                    geocoderFailed = true;
                }
            } else {
                geocoderFailed = true;
            }

            // Google's provider can time out (DEADLINE_EXCEEDED) on some emulators, and its
            // coverage differs from OpenStreetMap's, so a failed or empty lookup gets a second source.
            if (found.isEmpty()) {
                try {
                    found = fallback.search(search, max);
                } catch (IOException e) {
                    Log.w(TAG, "OpenStreetMap lookup failed", e);
                    if (geocoderFailed) {
                        mainThread.post(() -> callback.onError(
                                new IOException("Couldn't search for locations. Check your connection and try again.")));
                        return;
                    }
                }
            }

            List<Location> result = found;
            mainThread.post(() -> {
                if (result.isEmpty()) {
                    callback.onError(new NoSuchElementException("Couldn't find \"" + search + "\"."));
                } else {
                    callback.onSuccess(result);
                }
            });
        });
    }

    /** Gives up on Android's Geocoder after a few seconds instead of leaving the user waiting. */
    private List<Location> findWithTimeLimit(String search, int max) throws IOException, TimeoutException {
        Future<List<Location>> lookup = geocoderCalls.submit(() -> findWithRetry(search, max));
        try {
            return lookup.get(GEOCODER_TIME_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            lookup.cancel(true);
            // A provider that hangs once tends to keep hanging, so stop waiting on it.
            geocoderTimedOut = true;
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Location lookup was interrupted.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;
            }
            throw new IOException("Location lookup failed.", cause);
        }
    }

    /** The location service sometimes fails briefly, so a failed lookup gets one more try. */
    private List<Location> findWithRetry(String search, int max) throws IOException {
        try {
            return find(search, max);
        } catch (IOException e) {
            Log.w(TAG, "Location lookup failed, trying again", e);
            return find(search, max);
        }
    }

    /** Blocks until the location service answers, so this must stay off the main thread. */
    @SuppressWarnings("deprecation")
    private List<Location> find(String search, int max) throws IOException {
        List<Address> matches = geocoder.getFromLocationName(search, max,
                NEARBY_SOUTH, NEARBY_WEST, NEARBY_NORTH, NEARBY_EAST);
        if (matches == null || matches.isEmpty()) {
            matches = geocoder.getFromLocationName(search, max);
        }
        List<Location> places = new ArrayList<>();
        if (matches == null) {
            return places;
        }
        for (Address match : matches) {
            if (!match.hasLatitude() || !match.hasLongitude()) {
                continue;
            }
            String address = match.getAddressLine(0);
            Location place = new Location(address != null ? address : search,
                    match.getLatitude(), match.getLongitude());
            if (place.isValid()) {
                places.add(place);
            }
        }
        return places;
    }
}
