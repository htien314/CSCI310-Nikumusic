package com.example.trojanrideshare.service;
import com.example.trojanrideshare.model.*;
import java.util.*;
/** Shared history filters; radius units and geometry match Nikhil's ride search. */
public final class HistoryService {
    public List<Ride> filter(List<Ride> rides, String keyword, String status, String participant,
            Location origin, Location destination, double radiusMiles, boolean oldestFirst) {
        if (!Double.isFinite(radiusMiles) || radiusMiles <= 0 || radiusMiles > 100)
            throw new IllegalArgumentException("Choose a radius above 0 and at most 100 miles.");
        String text = clean(keyword), person = clean(participant);
        List<Ride> result = new ArrayList<>();
        for (Ride ride : rides) {
            if (ride.getActivityStatus() != ActivityStatus.COMPLETED && ride.getActivityStatus() != ActivityStatus.CANCELLED) continue;
            if (!"All".equalsIgnoreCase(status) && !ride.getActivityStatus().name().equalsIgnoreCase(status)) continue;
            String route = clean(ride.getTitle()) + " " + clean(ride.getOrigin() == null ? "" : ride.getOrigin().getAddress())
                    + " " + clean(ride.getDestination() == null ? "" : ride.getDestination().getAddress());
            if (!route.contains(text)) continue;
            boolean found = person.isEmpty();
            for (Participant p : ride.getParticipants()) if (clean(p.getDisplayName()).contains(person) || clean(p.getUserId()).contains(person)) found = true;
            if (!found) continue;
            if (origin != null && !com.example.trojanrideshare.util.Distance.withinRadius(origin, ride.getOrigin(), radiusMiles)) continue;
            if (destination != null && !com.example.trojanrideshare.util.Distance.withinRadius(destination, ride.getDestination(), radiusMiles)) continue;
            result.add(ride);
        }
        Comparator<Ride> byDate = Comparator.comparingLong(r -> r.getCurrentDepartureTime() == null ? 0L : r.getCurrentDepartureTime().toDate().getTime());
        result.sort(oldestFirst ? byDate : byDate.reversed());
        return result;
    }
    private static String clean(String text) { return text == null ? "" : text.trim().toLowerCase(Locale.US); }
}
