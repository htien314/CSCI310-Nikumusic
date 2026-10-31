
package com.example.trojanrideshare.ui.history;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trojanrideshare.R;

public class HistoryFragment extends Fragment {

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        EditText search = view.findViewById(R.id.historySearch);
        Spinner statusFilter = view.findViewById(R.id.historyStatusFilter);
        Spinner sort = view.findViewById(R.id.historySort);
        TextView message = view.findViewById(R.id.historyMessage);
        RecyclerView rideList = view.findViewById(R.id.historyRideList);

        rideList.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        String[] statusOptions = {
                "All",
                "Completed",
                "Cancelled"
        };

        String[] sortOptions = {
                "Newest First",
                "Oldest First"
        };

        ArrayAdapter<String> statusAdapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        statusOptions
                );

        statusAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        statusFilter.setAdapter(statusAdapter);

        ArrayAdapter<String> sortAdapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        sortOptions
                );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        sort.setAdapter(sortAdapter);

        // Ride data will be connected after integrating
        // the team's ride repository.
        message.setText("No ride history available yet.");
    }
}
