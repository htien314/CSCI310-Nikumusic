package com.example.trojanrideshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/** One request per ride/user. Cancelled pending requests can be submitted again. */
public class JoinRequest {
    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELLED }
    @DocumentId private String id;
    private String rideId;
    private String userId;
    private String displayName;
    private String message;
    private Status status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    public JoinRequest() { }
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getRideId() { return rideId; }
    public void setRideId(String value) { rideId = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp value) { createdAt = value; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp value) { updatedAt = value; }
}
