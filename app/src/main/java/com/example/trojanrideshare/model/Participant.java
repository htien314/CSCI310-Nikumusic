package com.example.trojanrideshare.model;

import com.google.firebase.Timestamp;

/** A rider in a ride, including the organizer. Stored inside a Ride document. */
public class Participant {

    private String userId;
    private String displayName;
    private Timestamp acceptedAt;

    /** Required by Firestore. */
    public Participant() {
    }

    public Participant(String userId, String displayName, Timestamp acceptedAt) {
        this.userId = userId;
        this.displayName = displayName;
        this.acceptedAt = acceptedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** When this rider joined. Decides who becomes organizer if the organizer leaves (UR-4.7). */
    public Timestamp getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Timestamp acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
