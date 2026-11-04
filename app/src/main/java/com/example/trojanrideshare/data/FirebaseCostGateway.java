package com.example.trojanrideshare.data;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.service.CostGateway;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.HashMap;
import java.util.Map;
public final class FirebaseCostGateway implements CostGateway {
    private final RideRepository rides;
    public FirebaseCostGateway(RideRepository rides) { this.rides = rides; }
    @Override public void execute(String action, String rideId, long totalCents,
            Map<String, Long> shares, String riderId, Callback<Ride> callback) {
        Map<String,Object> data = new HashMap<>();
        data.put("action",action); data.put("rideId",rideId);
        if (action.equals("complete")) {
            data.put("totalCents",totalCents);
            if (shares != null) data.put("customShares",shares);
        }
        if (riderId != null) data.put("riderId",riderId);
        FirebaseFunctions.getInstance("us-central1").getHttpsCallable("rideCosts").call(data)
                .addOnSuccessListener(result -> rides.getById(rideId,callback))
                .addOnFailureListener(callback::onError);
    }
}
