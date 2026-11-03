package com.example.trojanrideshare.service;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Callback;
import java.util.Map;
/** Atomic, authenticated cost mutations in the shared Firebase backend. */
public interface CostGateway {
    void execute(String action, String rideId, long totalCents, Map<String, Long> customShares,
                 String riderId, Callback<Ride> callback);
}
