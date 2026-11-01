package com.example.trojanrideshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

/**
 * A ride, stored in the {@code rides} collection. Input rules such as the 2-6 rider
 * limit are checked in RideService, not here.
 */
public class Ride {

    @DocumentId
    private String id;
    private String title;
    private Location origin;
    private Location destination;
    private Timestamp departureTime;
    private Timestamp delayedDepartureTime;
    private int maxRiders;
    private long expectedCostCents;
    private String note;
    private String organizerId;
    private List<Participant> participants = new ArrayList<>();
    private ActivityStatus activityStatus;
    private CapacityStatus capacityStatus;

    /** Required by Firestore. */
    public Ride() {
    }

    /** Builds a new ride: On-Time, with the organizer as its first participant (UR-2.4). */
    public static Ride createNew(String title, Location origin, Location destination,
                                 Timestamp departureTime, int maxRiders, long expectedCostCents,
                                 String note, String organizerId, String organizerName,
                                 Timestamp createdAt) {
        Ride ride = new Ride();
        ride.title = title;
        ride.origin = origin;
        ride.destination = destination;
        ride.departureTime = departureTime;
        ride.maxRiders = maxRiders;
        ride.expectedCostCents = expectedCostCents;
        ride.note = note;
        ride.organizerId = organizerId;
        ride.participants.add(new Participant(organizerId, organizerName, createdAt));
        ride.activityStatus = ActivityStatus.ON_TIME;
        ride.syncCapacityStatus();
        return ride;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Location getOrigin() {
        return origin;
    }

    public void setOrigin(Location origin) {
        this.origin = origin;
    }

    public Location getDestination() {
        return destination;
    }

    public void setDestination(Location destination) {
        this.destination = destination;
    }

    /** The original departure time. Never changes, and the 3-hour cutoff uses it (UR-2.5, UR-2.7). */
    public Timestamp getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Timestamp departureTime) {
        this.departureTime = departureTime;
    }

    /** The new time entered when marking the ride Delayed (UR-2.6), or null if there is none. */
    public Timestamp getDelayedDepartureTime() {
        return delayedDepartureTime;
    }

    public void setDelayedDepartureTime(Timestamp delayedDepartureTime) {
        this.delayedDepartureTime = delayedDepartureTime;
    }

    public int getMaxRiders() {
        return maxRiders;
    }

    public void setMaxRiders(int maxRiders) {
        this.maxRiders = maxRiders;
    }

    public long getExpectedCostCents() {
        return expectedCostCents;
    }

    public void setExpectedCostCents(long expectedCostCents) {
        this.expectedCostCents = expectedCostCents;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public ActivityStatus getActivityStatus() {
        return activityStatus;
    }

    public void setActivityStatus(ActivityStatus activityStatus) {
        this.activityStatus = activityStatus;
    }

    public CapacityStatus getCapacityStatus() {
        return capacityStatus;
    }

    public void setCapacityStatus(CapacityStatus capacityStatus) {
        this.capacityStatus = capacityStatus;
    }

    /** The time for display, search and sorting: the delayed time if there is one, otherwise the original. */
    @Exclude
    public Timestamp getCurrentDepartureTime() {
        return delayedDepartureTime != null ? delayedDepartureTime : departureTime;
    }

    @Exclude
    public int getAvailableSeats() {
        return Math.max(0, maxRiders - participants.size());
    }

    @Exclude
    public boolean isOrganizer(String userId) {
        return organizerId != null && organizerId.equals(userId);
    }

    /** The organizer's name from the participants, or null if they are not listed. */
    @Exclude
    public String getOrganizerName() {
        for (Participant participant : participants) {
            if (isOrganizer(participant.getUserId())) {
                return participant.getDisplayName();
            }
        }
        return null;
    }

    /** Cancelled and Completed rides can't change any more. */
    @Exclude
    public boolean isFinished() {
        return activityStatus == ActivityStatus.CANCELLED || activityStatus == ActivityStatus.COMPLETED;
    }

    /** Call after changing the participants or activity status (UR-2.8, UR-2.11). */
    public void syncCapacityStatus() {
        if (isFinished()) {
            capacityStatus = CapacityStatus.NOT_APPLICABLE;
        } else if (getAvailableSeats() == 0) {
            capacityStatus = CapacityStatus.FULL;
        } else {
            capacityStatus = CapacityStatus.OPEN;
        }
    }
}
