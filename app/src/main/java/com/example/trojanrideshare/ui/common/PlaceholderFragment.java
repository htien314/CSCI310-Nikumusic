package com.example.trojanrideshare.ui.common;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.trojanrideshare.R;

/**
 * Temporary screen used by every destination in nav_graph.xml until its owner
 * builds the real one. To replace it, change that destination's android:name
 * in res/navigation/nav_graph.xml to your own Fragment class.
 */
public class PlaceholderFragment extends Fragment {

    public static final String ARG_TITLE = "title";
    public static final String ARG_OWNER = "owner";

    public PlaceholderFragment() {
        super(R.layout.fragment_placeholder);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE, "") : "";
        String owner = args != null ? args.getString(ARG_OWNER, "") : "";
        ((TextView) view.findViewById(R.id.placeholder_title)).setText(title);
        ((TextView) view.findViewById(R.id.placeholder_owner))
                .setText(getString(R.string.placeholder_owner, owner));
    }
}
