package com.example.trojanrideshare.ui.history;
import com.example.trojanrideshare.model.*;
import com.example.trojanrideshare.util.MoneyFormatter;
public final class HistoryRideItem {
    private final Ride ride;
    private final String userId;
    public HistoryRideItem(Ride ride, String userId) { this.ride=ride; this.userId=userId; }
    public String getId() { return ride.getId(); }
    public String getTitle() { return ride.getTitle(); }
    public String getRoute() { return (ride.getOrigin()==null?"Unknown origin":ride.getOrigin().getAddress()) + " → " + (ride.getDestination()==null?"Unknown destination":ride.getDestination().getAddress()); }
    public long getDepartureMillis() { return ride.getCurrentDepartureTime()==null?0L:ride.getCurrentDepartureTime().toDate().getTime(); }
    public String getStatus() { return ride.getActivityStatus()==ActivityStatus.COMPLETED?"Completed":"Cancelled"; }
    public String getDetails() {
        String result = "Participants: " + ride.getParticipants().size();
        if (ride.getActivityStatus()!=ActivityStatus.COMPLETED) return result;
        CostShare mine=ride.getCostShares()==null?null:ride.getCostShares().get(userId);
        if (mine==null) return result+"\nYour assigned share: unavailable";
        return result+"\nYour assigned share: "+MoneyFormatter.format(mine.getAmountCents())
                +" · "+(mine.isPaid()?"Paid":"Unpaid")+(mine.isFlagged()?" · Overdue flag":"");
    }
}
