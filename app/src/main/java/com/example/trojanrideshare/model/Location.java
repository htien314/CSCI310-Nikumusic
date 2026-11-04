package com.example.trojanrideshare.model;

import com.google.firebase.firestore.Exclude;

/** A geocoded place, as returned by LocationGateway. Stored inside a Ride document. */
public class Location {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private String address;
    private double latitude;
    private double longitude;

    /** Required by Firestore. */
    public Location() {
    }

    public Location(String address, double latitude, double longitude) {
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * Straight-line distance to another place in miles, using the haversine formula.
     * Search uses this for the radius check and for proximity (UR-3.3, UR-3.7).
     */
    public double distanceMilesTo(Location other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** True if this has an address and real coordinates. */
    @Exclude
    public boolean isValid() {
        return address != null && !address.trim().isEmpty()
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }
}
