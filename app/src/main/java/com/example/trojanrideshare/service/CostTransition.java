package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.ActivityStatus;
import com.example.trojanrideshare.model.CostShare;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completion and payment rules for one action, applied inside the app's Firestore transaction
 * (Java port of the team's functions/costs.js). Firestore rules re-check the actor, the
 * departure and seven-day cutoffs against server time, and the share totals.
 */
public final class CostTransition {

    public static final String COMPLETE = "complete";
    public static final String PAID = "paid";
    public static final String FLAG = "flag";

    static final long SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private CostTransition() {
    }

    /** The shares to save when the organizer completes the ride; the caller stamps completedAt. */
    public static Map<String, CostShare> complete(Ride ride, String uid, long totalCents,
                                                  Map<String, Long> customShares, Timestamp now) {
        if (uid == null) throw new RideRuleException("Please log in first.");
        if (ride == null) throw new RideRuleException("Ride not found.");
        if (!uid.equals(ride.getOrganizerId())) throw new RideRuleException("Only the organizer can complete this ride.");
        if (ride.isFinished()) throw new RideRuleException("This ride is already cancelled or completed.");
        Timestamp departure = ride.getCurrentDepartureTime();
        if (departure == null || now.compareTo(departure) < 0) {
            throw new RideRuleException("The ride cannot be completed before departure.");
        }
        if (totalCents <= 0) throw new RideRuleException("Enter a positive total in whole cents.");

        List<String> ids = new ArrayList<>();
        for (Participant participant : ride.getParticipants()) ids.add(participant.getUserId());
        if (ids.isEmpty() || ids.contains(null) || new HashSet<>(ids).size() != ids.size() || !ids.contains(uid)) {
            throw new RideRuleException("Ride participants are invalid.");
        }

        Map<String, Long> amounts;
        try {
            if (customShares == null) {
                amounts = CostCalculator.splitEqual(totalCents, ids);
            } else {
                CostCalculator.validateCustom(totalCents, ids, customShares);
                amounts = customShares;
            }
        } catch (IllegalArgumentException e) {
            throw new RideRuleException("Provide a share of $0.00 or more for every participant, totaling the actual cost.");
        }

        Map<String, CostShare> shares = new LinkedHashMap<>();
        for (String id : ids) shares.put(id, new CostShare(id, amounts.get(id)));
        return shares;
    }

    /** The ride's shares after a rider marks their own share Paid, or the organizer flags an unpaid one. */
    public static Map<String, CostShare> updateShare(Ride ride, String action, String uid, String riderId, Timestamp now) {
        if (uid == null) throw new RideRuleException("Please log in first.");
        if (ride == null) throw new RideRuleException("Ride not found.");
        if (ride.getActivityStatus() != ActivityStatus.COMPLETED || ride.getCompletedAt() == null
                || ride.getCostShares() == null) {
            throw new RideRuleException("Complete this ride first.");
        }
        if (riderId == null || !ride.getCostShares().containsKey(riderId)) {
            throw new RideRuleException("This rider has no cost share.");
        }

        Map<String, CostShare> shares = new LinkedHashMap<>();
        for (Map.Entry<String, CostShare> entry : ride.getCostShares().entrySet()) {
            CostShare original = entry.getValue();
            CostShare copy = new CostShare(entry.getKey(), original.getAmountCents());
            if (original.isPaid()) copy.markPaid();
            else if (original.isFlagged()) copy.setFlagged(true);
            shares.put(entry.getKey(), copy);
        }
        CostShare share = shares.get(riderId);

        if (PAID.equals(action)) {
            if (!riderId.equals(uid)) throw new RideRuleException("You may mark only your own share Paid.");
            if (share.isPaid()) throw new RideRuleException("This share is already Paid.");
            share.markPaid();
        } else if (FLAG.equals(action)) {
            if (!uid.equals(ride.getOrganizerId())) throw new RideRuleException("Only the organizer may flag unpaid riders.");
            if (share.isPaid()) throw new RideRuleException("Paid riders cannot be flagged.");
            if (now.toDate().getTime() - ride.getCompletedAt().toDate().getTime() < SEVEN_DAYS_MILLIS) {
                throw new RideRuleException("Flagging is available seven days after cost recording.");
            }
            share.setFlagged(true);
        } else {
            throw new RideRuleException("Unknown cost action.");
        }
        return shares;
    }
}
