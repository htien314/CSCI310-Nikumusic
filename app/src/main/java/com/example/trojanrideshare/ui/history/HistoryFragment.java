package com.example.trojanrideshare.ui.history;

import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.*;
import com.example.trojanrideshare.model.*;
import com.example.trojanrideshare.service.HistoryService;
import com.example.trojanrideshare.ui.common.LocationPickerView;
import com.example.trojanrideshare.util.Callback;
import com.google.firebase.auth.FirebaseAuth;
import java.util.*;

/** User history with shared location matching and actual assigned payment information. */
public final class HistoryFragment extends Fragment {
    private final HistoryRepository repository=new HistoryRepository();
    private final HistoryService service=new HistoryService();
    private final List<Ride> rides=new ArrayList<>();
    private HistoryRideAdapter adapter;
    private EditText search,participant,radius;
    private Spinner status,sort;
    private TextView message;
    private LocationPickerView originPicker,destinationPicker;
    private Location origin,destination;
    private String userId;
    private double radiusMiles=5;
    private int version,filterVersion;
    public HistoryFragment() { super(R.layout.fragment_history); }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        super.onViewCreated(view,saved);
        search=view.findViewById(R.id.historySearch); participant=view.findViewById(R.id.historyParticipant);
        radius=view.findViewById(R.id.historyRadius); status=view.findViewById(R.id.historyStatusFilter);
        sort=view.findViewById(R.id.historySort); message=view.findViewById(R.id.historyMessage);
        originPicker=view.findViewById(R.id.historyOrigin); destinationPicker=view.findViewById(R.id.historyDestination);
        originPicker.setGateway(new GeocoderLocationGateway(requireContext()));
        destinationPicker.setGateway(new GeocoderLocationGateway(requireContext()));
        setSpinner(status,new String[]{"All","Completed","Cancelled"});
        setSpinner(sort,new String[]{"Newest First","Oldest First"});
        RecyclerView list=view.findViewById(R.id.historyRideList);list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter=new HistoryRideAdapter();list.setAdapter(adapter);
        TextWatcher watcher=new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c) { }
            public void onTextChanged(CharSequence s,int a,int b,int c) { render(); }
            public void afterTextChanged(Editable e) { }
        };
        search.addTextChangedListener(watcher);participant.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int position,long id) { render(); }
            public void onNothingSelected(AdapterView<?> p) { }
        };
        status.setOnItemSelectedListener(listener);sort.setOnItemSelectedListener(listener);
        Button more=view.findViewById(R.id.historyMoreFilters);
        View advanced=view.findViewById(R.id.historyAdvancedFilters);
        more.setOnClickListener(v -> {
            boolean expanded=advanced.getVisibility()!=View.VISIBLE;
            advanced.setVisibility(expanded?View.VISIBLE:View.GONE);
            more.setText(expanded?"Hide extra filters":"More filters");
        });
        view.findViewById(R.id.historyApply).setOnClickListener(v -> applyLocations(view));
    }
    private void setSpinner(Spinner spinner,String[] values) {
        ArrayAdapter<String> items=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,values);
        items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(items);
    }
    @Override public void onResume() { super.onResume();load(); }
    private void load() {
        View screen=getView();if(screen==null) return;
        int current=++version;filterVersion++;rides.clear();adapter.setRides(new ArrayList<>());
        userId=FirebaseAuth.getInstance().getUid();
        if(userId==null) {message.setText("Please log in to view your history.");return;}
        String uid=userId;message.setText("Loading history…");
        repository.getMyRides(uid,snapshots -> {
            if(getView()!=screen || current!=version || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
            for(com.google.firebase.firestore.DocumentSnapshot doc:snapshots) {
                try {Ride ride=doc.toObject(Ride.class);if(ride!=null) rides.add(ride);}
                catch(RuntimeException error) {android.util.Log.w("History","Skipping unreadable ride",error);}
            }
            render();
        },error -> {if(getView()==screen && current==version && uid.equals(FirebaseAuth.getInstance().getUid())) message.setText("Could not load history. Please reopen this screen to retry.");});
    }
    private void applyLocations(View screen) {
        double selected;
        try { selected=radius.getText().toString().trim().isEmpty()?5:Double.parseDouble(radius.getText().toString());
            if(!Double.isFinite(selected) || selected<=0 || selected>100) throw new IllegalArgumentException();
        } catch(RuntimeException error) {message.setText("Choose a radius above 0 and at most 100 miles.");return;}
        int current=++filterVersion;
        String uid=FirebaseAuth.getInstance().getUid();
        Callback<Location> destinationResult=new Callback<Location>() {
            public void onSuccess(Location ignored) { }
            public void onError(Exception e) {if(getView()==screen && current==filterVersion) message.setText(e.getMessage());}
        };
        resolve(originPicker,new Callback<Location>() {
            public void onSuccess(Location pickup) {
                resolve(destinationPicker,new Callback<Location>() {
                    public void onSuccess(Location dropoff) {
                        if(getView()!=screen || current!=filterVersion || !Objects.equals(uid,FirebaseAuth.getInstance().getUid())) return;
                        origin=pickup;destination=dropoff;radiusMiles=selected;render();
                    }
                    public void onError(Exception e) {destinationResult.onError(e);}
                });
            }
            public void onError(Exception e) {destinationResult.onError(e);}
        });
    }
    private void resolve(LocationPickerView picker,Callback<Location> callback) {
        if(picker.getText().isEmpty()) callback.onSuccess(null);else picker.resolve(callback);
    }
    private void render() {
        if(adapter==null || getView()==null) return;
        if(userId==null || !userId.equals(FirebaseAuth.getInstance().getUid())) {
            adapter.setRides(new ArrayList<>());message.setText("Please reopen history for your current account.");return;
        }
        List<Ride> filtered=service.filter(rides,search.getText().toString(),String.valueOf(status.getSelectedItem()),
                participant.getText().toString(),origin,destination,radiusMiles,sort.getSelectedItemPosition()==1);
        List<HistoryRideItem> items=new ArrayList<>();for(Ride ride:filtered)items.add(new HistoryRideItem(ride,userId));
        adapter.setRides(items);message.setText(items.isEmpty()?"No matching completed or cancelled rides.":items.size()+" ride(s) shown");
    }
    @Override public void onDestroyView() {version++;filterVersion++;adapter=null;super.onDestroyView();}
}
