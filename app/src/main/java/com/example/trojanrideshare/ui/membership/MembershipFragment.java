package com.example.trojanrideshare.ui.membership;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.example.trojanrideshare.R;
import com.example.trojanrideshare.data.FirebaseJoinRequestRepository;
import com.example.trojanrideshare.model.JoinRequest;
import com.example.trojanrideshare.model.Participant;
import com.example.trojanrideshare.model.Ride;
import com.example.trojanrideshare.util.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Membership hub and ride-specific request/inbox screen, independent of other feature screens. */
public final class MembershipFragment extends Fragment {
    private MembershipViewModel model;
    private LinearLayout content;
    private FirebaseAuth.AuthStateListener authListener;
    private String rideId;
    public MembershipFragment() { super(R.layout.fragment_membership); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        rideId = getArguments() == null ? null : getArguments().getString(Constants.ARG_RIDE_ID);
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> type) {
                return (T) new MembershipViewModel(new FirebaseJoinRequestRepository(
                        FirebaseFirestore.getInstance(), FirebaseFunctions.getInstance("us-central1")));
            }
        }).get(MembershipViewModel.class);
        content = view.findViewById(R.id.membership_content);
        ((TextView) view.findViewById(R.id.membership_title)).setText(rideId == null
                ? R.string.membership_title : R.string.membership_details_title);
        view.findViewById(R.id.membership_refresh).setOnClickListener(v -> reload());
        model.getRides().observe(getViewLifecycleOwner(), value -> render());
        model.getMine().observe(getViewLifecycleOwner(), value -> render());
        model.getInbox().observe(getViewLifecycleOwner(), value -> render());
        model.getBusy().observe(getViewLifecycleOwner(), value -> {
            view.findViewById(R.id.membership_refresh).setEnabled(!Boolean.TRUE.equals(value)); render();
        });
        model.getLoading().observe(getViewLifecycleOwner(), value -> view.findViewById(R.id.membership_loading)
                .setVisibility(Boolean.TRUE.equals(value) ? View.VISIBLE : View.GONE));
        model.getNotice().observe(getViewLifecycleOwner(), value ->
                ((TextView) view.findViewById(R.id.membership_notice)).setText(value));
        authListener = auth -> {
            model.start(auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid()); render();
        };
    }
    @Override public void onStart() {
        super.onStart(); FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }
    @Override public void onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener); super.onStop();
    }
    @Override public void onDestroyView() { content = null; super.onDestroyView(); }
    private void reload() {
        model.start(FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid()); render();
    }
    private void render() {
        if (content == null) return;
        content.removeAllViews();
        if (model.getUserId() == null) {
            button("Log in", () -> NavHostFragment.findNavController(this).navigate(R.id.action_global_login)); return;
        }
        List<Ride> rides = model.getRides().getValue();
        if (rides == null) return;
        if (rideId != null) {
            Ride ride = findRide(rideId);
            if (ride == null) { text("Loading ride… If it does not appear, refresh or return to your rides."); return; }
            showRide(ride); return;
        }
        model.watchInbox(null);
        heading("Your rides");
        int count = 0;
        for (Ride ride : rides) if (ride.isOrganizer(model.getUserId()) || isMember(ride)) {
            rideLink(ride); count++;
        }
        if (count == 0) text("You have not joined or organized a ride yet.");
        heading("Pending requests"); count = 0;
        for (JoinRequest request : sorted(model.getMine().getValue())) if (request.getStatus() == JoinRequest.Status.PENDING) {
            Ride ride = findRide(request.getRideId());
            text((ride == null ? "Ride" : ride.getTitle()) + " · Requested " + time(request.getCreatedAt()));
            if (request.getMessage() != null && !request.getMessage().isEmpty()) text(request.getMessage());
            button("View ride", () -> open(request.getRideId()));
            button("Cancel request", () -> confirm("Cancel this pending request?", "Cancel request",
                    () -> model.act("cancel", request.getRideId(), null, null))); count++;
        }
        if (count == 0) text("You have no pending requests.");
        // This entry point keeps joining usable while Nikhil connects the search results.
        heading("Open rides to request"); count = 0;
        for (Ride ride : rides) if (!ride.isFinished() && !ride.isOrganizer(model.getUserId())
                && !isMember(ride) && ride.getAvailableSeats() > 0) { rideLink(ride); count++; }
        if (count == 0) text("There are no other open rides right now.");
    }
    private void showRide(Ride ride) {
        heading(ride.getTitle());
        text((ride.getOrigin() == null ? "" : ride.getOrigin().getAddress()) + " → "
                + (ride.getDestination() == null ? "" : ride.getDestination().getAddress()));
        text(time(ride.getCurrentDepartureTime()) + " · " + ride.getParticipants().size() + "/" + ride.getMaxRiders() + " riders");
        text("Status: " + label(ride.getActivityStatus().name()) + " · " + label(ride.getCapacityStatus().name()));
        heading("Participants");
        for (Participant participant : ride.getParticipants()) text(participant.getDisplayName()
                + (Objects.equals(participant.getUserId(), ride.getOrganizerId()) ? " (organizer)" : ""));
        JoinRequest own = null;
        for (JoinRequest request : model.getMine().getValue()) if (ride.getId().equals(request.getRideId())) own = request;
        if (own != null) text("Your request: " + label(own.getStatus().name()) + " · " + time(own.getCreatedAt()));
        if (isMember(ride) && !ride.isFinished()) {
            text("Leaving is allowed at least three hours before the original departure: " + time(ride.getDepartureTime()));
            button("Leave ride", () -> confirm(ride.isOrganizer(model.getUserId())
                    ? "Leave this ride? The most recently accepted remaining rider will become organizer. If nobody remains, the ride will be cancelled."
                    : "Leave this ride?", "Leave ride", () -> model.act("leave", ride.getId(), null, null)));
        } else if (own != null && own.getStatus() == JoinRequest.Status.PENDING) {
            button("Cancel request", () -> confirm("Cancel this pending request?", "Cancel request",
                    () -> model.act("cancel", ride.getId(), null, null)));
        } else if (own != null && own.getStatus() == JoinRequest.Status.REJECTED) {
            text("Your previous request was rejected. You cannot request this ride again.");
        } else if (!ride.isFinished() && !ride.isOrganizer(model.getUserId()) && !isMember(ride) && ride.getAvailableSeats() > 0) {
            button("Request to join", () -> requestDialog(ride.getId()));
        }
        if (ride.isOrganizer(model.getUserId())) {
            model.watchInbox(ride.getId()); heading("Organizer inbox");
            int count = 0;
            for (JoinRequest request : sorted(model.getInbox().getValue())) if (request.getStatus() == JoinRequest.Status.PENDING) {
                text(request.getDisplayName() + " · " + time(request.getCreatedAt()));
                if (request.getMessage() != null && !request.getMessage().isEmpty()) text(request.getMessage());
                if (!ride.isFinished()) {
                    button("Accept " + request.getDisplayName(), () -> model.act("accept", ride.getId(), request.getId(), null));
                    button("Reject " + request.getDisplayName(), () -> confirm("Reject this request? This rider will not be able to request this ride again.",
                            "Reject request", () -> model.act("reject", ride.getId(), request.getId(), null)));
                }
                count++;
            }
            if (count == 0) text("No pending requests for this ride.");
        } else model.watchInbox(null);
    }
    private void requestDialog(String id) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.membership_message_hint);
        input.setMinLines(2); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Request to join").setView(input)
                .setPositiveButton("Send request", (dialog, which) -> model.act("request", id, null, input.getText().toString()))
                .setNegativeButton("Cancel", null).show();
    }
    private void confirm(String message, String action, Runnable run) {
        new MaterialAlertDialogBuilder(requireContext()).setMessage(message)
                .setPositiveButton(action, (dialog, which) -> run.run()).setNegativeButton("Keep", null).show();
    }
    private void open(String id) {
        Bundle args = new Bundle(); args.putString(Constants.ARG_RIDE_ID, id);
        NavHostFragment.findNavController(this).navigate(R.id.action_global_requestsInbox, args);
    }
    private void rideLink(Ride ride) { button(ride.getTitle() + " · " + time(ride.getCurrentDepartureTime()), () -> open(ride.getId())); }
    private Ride findRide(String id) {
        for (Ride ride : model.getRides().getValue()) if (id.equals(ride.getId())) return ride;
        return null;
    }
    private boolean isMember(Ride ride) {
        for (Participant p : ride.getParticipants()) if (Objects.equals(p.getUserId(), model.getUserId())) return true;
        return false;
    }
    private static List<JoinRequest> sorted(List<JoinRequest> values) {
        List<JoinRequest> result = new ArrayList<>(values == null ? new ArrayList<>() : values);
        result.sort(Comparator.comparing(JoinRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
    private void heading(String value) { TextView view = text(value); view.setTextSize(20); view.setPadding(0, 24, 0, 8); }
    private TextView text(String value) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(16); view.setPadding(0, 6, 0, 6);
        content.addView(view); return view;
    }
    private void button(String title, Runnable action) {
        Button button = new Button(requireContext()); button.setText(title); button.setMinHeight(48);
        button.setEnabled(!Boolean.TRUE.equals(model.getBusy().getValue()));
        button.setOnClickListener(view -> action.run()); content.addView(button);
    }
    private static String label(String value) { return value.toLowerCase(java.util.Locale.US).replace('_', ' '); }
    private static String time(Timestamp value) {
        return value == null ? "Time unavailable" : value.toDate().toInstant().atZone(Constants.DISPLAY_ZONE)
                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a z", java.util.Locale.US));
    }
}
