package com.example.trojanrideshare.data;

import com.example.trojanrideshare.model.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backup place lookup through OpenStreetMap's free Nominatim service, used when Android's
 * Geocoder fails or times out (Google Play services on some emulators can't answer).
 * Nominatim's policy asks for an identifying User-Agent and at most one request per second;
 * the app only calls it after a failed Geocoder lookup the user started.
 */
class NominatimLocationGateway {

    private static final String ENDPOINT = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "TrojanRideShare/1.0 (USC CSCI 310 class project)";
    private static final int TIMEOUT_MILLIS = 8000;

    // Prefer Southern California, like the Geocoder search, without excluding other places.
    private static final String VIEWBOX = "-120,35,-116,32.5";

    /** Blocks until Nominatim answers, so this must stay off the main thread. */
    List<Location> search(String query, int maxResults) throws IOException {
        String url = ENDPOINT + "?format=jsonv2&limit=" + Math.max(1, maxResults)
                + "&viewbox=" + VIEWBOX
                + "&q=" + URLEncoder.encode(query, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Language", "en-US");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Nominatim returned HTTP " + status);
            }
            try (InputStream body = connection.getInputStream()) {
                return parse(readAll(body));
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Places from a Nominatim jsonv2 response, skipping entries without usable coordinates. */
    static List<Location> parse(String json) throws IOException {
        List<Location> places = new ArrayList<>();
        try {
            JSONArray results = new JSONArray(json);
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                Location place = new Location(
                        shortAddress(result.optString("display_name")),
                        Double.parseDouble(result.optString("lat", "NaN")),
                        Double.parseDouble(result.optString("lon", "NaN")));
                // NaN coordinates fail isValid's range checks.
                if (place.isValid()) {
                    places.add(place);
                }
            }
        } catch (JSONException | NumberFormatException e) {
            throw new IOException("Unexpected Nominatim response", e);
        }
        return places;
    }

    /** "USC Village, South Hoover Street, …, Los Angeles County, California, 90089, United States" without the county and country. */
    static String shortAddress(String displayName) {
        StringBuilder address = new StringBuilder();
        for (String part : displayName.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.equals("United States")
                    || trimmed.toLowerCase(Locale.US).endsWith(" county")) {
                continue;
            }
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(trimmed);
        }
        return address.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
