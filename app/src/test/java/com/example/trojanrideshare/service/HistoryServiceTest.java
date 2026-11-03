package com.example.trojanrideshare.service;
import com.example.trojanrideshare.model.*;
import com.example.trojanrideshare.ui.history.HistoryRideItem;
import com.google.firebase.Timestamp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class HistoryServiceTest {
    private final HistoryService service=new HistoryService();
    private Ride ride(String id,ActivityStatus state,long time) {
        Ride r=new Ride();r.setId(id);r.setTitle("Airport trip");r.setActivityStatus(state);
        r.setDepartureTime(new Timestamp(new Date(time)));
        r.setOrigin(new Location("USC",34.02,-118.28));r.setDestination(new Location("LAX",33.94,-118.4));
        r.setParticipants(Arrays.asList(new Participant("alice","Hailey",new Timestamp(new Date(1)))));
        return r;
    }
    private List<Ride> filter(List<Ride> rides,String keyword,String status,String person,Location origin,Location destination,boolean oldest) {
        return service.filter(rides,keyword,status,person,origin,destination,5,oldest);
    }
    @Test public void combinesTextStatusParticipantAndLocationFilters() {
        Ride completed=ride("done",ActivityStatus.COMPLETED,1000);
        Ride cancelled=ride("cancel",ActivityStatus.CANCELLED,2000);
        Ride active=ride("active",ActivityStatus.ON_TIME,3000);
        List<Ride> result=filter(Arrays.asList(completed,cancelled,active),"lax","Completed","HAIL",completed.getOrigin(),completed.getDestination(),false);
        assertEquals(1,result.size());assertEquals("done",result.get(0).getId());
        assertTrue(filter(Arrays.asList(completed),"","All","stranger",null,null,false).isEmpty());
    }
    @Test public void radiusChecksBothEndpointsAndMissingLocations() {
        Ride r=ride("done",ActivityStatus.COMPLETED,1000);
        Location far=new Location("San Francisco",37.77,-122.42);
        assertTrue(filter(Arrays.asList(r),"","All","",far,null,false).isEmpty());
        assertTrue(filter(Arrays.asList(r),"","All","",null,far,false).isEmpty());
        r.setOrigin(null);
        assertTrue(filter(Arrays.asList(r),"","All","",far,null,false).isEmpty());
    }
    @Test public void usesCurrentDepartureForBothSortDirections() {
        Ride a=ride("a",ActivityStatus.COMPLETED,1000),b=ride("b",ActivityStatus.CANCELLED,2000);
        a.setDelayedDepartureTime(new Timestamp(new Date(3000)));
        assertEquals("a",filter(Arrays.asList(a,b),"","All","",null,null,false).get(0).getId());
        assertEquals("b",filter(Arrays.asList(a,b),"","All","",null,null,true).get(0).getId());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidRadius() {
        service.filter(Collections.emptyList(),"","All","",null,null,Double.NaN,false);
    }
    @Test public void historyShowsAssignedCostAndPaymentStateRatherThanEstimate() {
        Ride r=ride("done",ActivityStatus.COMPLETED,1000);r.setExpectedCostCents(9000);
        CostShare mine=new CostShare("alice",1234);mine.setFlagged(true);
        r.setCostShares(Collections.singletonMap("alice",mine));
        String text=new HistoryRideItem(r,"alice").getDetails();
        assertTrue(text.contains("12.34"));assertTrue(text.contains("Unpaid"));assertTrue(text.contains("Overdue"));assertFalse(text.contains("90.00"));
        mine.markPaid();assertTrue(new HistoryRideItem(r,"alice").getDetails().contains("Paid"));
    }
}
