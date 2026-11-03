
package com.example.trojanrideshare.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trojanrideshare.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryRideAdapter
        extends RecyclerView.Adapter<HistoryRideAdapter.RideViewHolder> {

    private final List<HistoryRideItem> rides = new ArrayList<>();

    public void setRides(List<HistoryRideItem> updatedRides) {
        rides.clear();
        rides.addAll(updatedRides);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_ride, parent, false);

        return new RideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RideViewHolder holder, int position) {

        HistoryRideItem ride = rides.get(position);

        holder.title.setText(ride.getTitle());
        holder.route.setText(ride.getRoute());

        String date = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT
        ).format(new Date(ride.getDepartureMillis()));

        holder.date.setText("Departure: " + date);
        holder.status.setText("Status: " + ride.getStatus());

        String cost = String.format(
                Locale.US,
                "Expected cost: $%.2f",
                ride.getCostCents() / 100.0
        );

        holder.cost.setText(cost);
    }

    @Override
    public int getItemCount() {
        return rides.size();
    }

    static class RideViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView route;
        final TextView date;
        final TextView status;
        final TextView cost;

        RideViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.historyRideTitle);
            route = itemView.findViewById(R.id.historyRideRoute);
            date = itemView.findViewById(R.id.historyRideDate);
            status = itemView.findViewById(R.id.historyRideStatus);
            cost = itemView.findViewById(R.id.historyRideCost);
        }
    }
}
