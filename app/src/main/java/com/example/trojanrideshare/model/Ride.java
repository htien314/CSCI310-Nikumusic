
package com.example.trojanrideshare.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A ride stored in the rides collection.
 * Input rules such as the 2-6 rider limit belong in RideService.
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

    // Final cost and payment information.
    private Long actualCostCents;
    private Map<String, CostShare> costShares;
    private Timestamp completedAt;

    private String note;
    private String organizerId;
    private List<Participant> participants = new ArrayList<>();
    // Participant IDs used for Firestore queries.
    private List<String> participantIds = new ArrayList<>();
    private ActivityStatus activityStatus;
    private CapacityStatus capacityStatus;

    /** Required by Firestore. */
    public Ride() {
    }

    /** Creates an on-time ride with the organizer as its first participant. */
    public static Ride createNew(
            String title,
            Location origin,
            Location destination,
            Timestamp departureTime,
            int maxRiders,
            long expectedCostCents,
            String note,
            String organizerId,
            String organizerName,
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

        ride.participants.add(
                new Participant(organizerId, organizerName, createdAt));
        ride.syncParticipantIds();

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

    /** Original departure time used for cancellation rules. */
    public Timestamp getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Timestamp departureTime) {
        this.departureTime = departureTime;
    }

    /** Updated departure time if the ride is delayed. */
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

    // Final ride cost, recorded after completion.
    public Long getActualCostCents() {
        return actualCostCents;
    }

    public void setActualCostCents(Long actualCostCents) {
        this.actualCostCents = actualCostCents;
    }

    // Individual participant shares and payment states.
    public Map<String, CostShare> getCostShares() {
        return costShares;
    }

    public void setCostShares(Map<String, CostShare> costShares) {
        this.costShares = costShares;
    }

    // Timestamp used for completion and payment tracking.
    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
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
        syncParticipantIds();
    }

    // Keep the searchable participant IDs synchronized.
    public void syncParticipantIds() {
        participantIds = new ArrayList<>();

        if (participants == null) {
            return;
        }

        for (Participant participant : participants) {
            if (participant != null
                    && participant.getUserId() != null
                    && !participant.getUserId().trim().isEmpty()
                    && !participantIds.contains(participant.getUserId())) {

                participantIds.add(participant.getUserId());
            }
        }
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
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

    /** Uses the delayed time when available. */
    @Exclude
    public Timestamp getCurrentDepartureTime() {
        return delayedDepartureTime != null
                ? delayedDepartureTime : departureTime;
    }

    @Exclude
    public int getAvailableSeats() {
        return Math.max(0, maxRiders - participants.size());
    }

    @Exclude
    public boolean isOrganizer(String userId) {
        return organizerId != null && organizerId.equals(userId);
    }

    /** Finds the organizer's display name. */
    @Exclude
    public String getOrganizerName() {
        for (Participant participant : participants) {
            if (isOrganizer(participant.getUserId())) {
                return participant.getDisplayName();
            }
        }
        return null;
    }

    /** Cancelled and completed rides cannot be changed as active rides. */
    @Exclude
    public boolean isFinished() {
        return activityStatus == ActivityStatus.CANCELLED
                || activityStatus == ActivityStatus.COMPLETED;
    }

    /** Updates capacity after participant or status changes. */
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
