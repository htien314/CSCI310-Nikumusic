package com.example.trojanrideshare.model;

import com.google.firebase.firestore.Exclude;

/** A geocoded place, as returned by LocationGateway. Stored inside a Ride document. */
public class Location {

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

    /** True if this has an address and real coordinates. */
    @Exclude
    public boolean isValid() {
        return address != null && !address.trim().isEmpty()
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }
}
