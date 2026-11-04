package com.example.trojanrideshare.ui.common;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.trojanrideshare.R;

/** Presentation helpers for rows that feature screens build dynamically. */
public final class UiPolish {
    private UiPolish() { }

    public static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    public static void card(View view) {
        view.setBackgroundResource(R.drawable.bg_card);
        int padding = dp(view, 16);
        view.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(view, 6), 0, dp(view, 8));
        view.setLayoutParams(params);
    }

    public static void heading(TextView view) {
        view.setTextColor(ContextCompat.getColor(view.getContext(), R.color.app_ink));
        view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        view.setTextSize(20);
        view.setPadding(0, dp(view, 24), 0, dp(view, 10));
        androidx.core.view.ViewCompat.setAccessibilityHeading(view, true);
    }
}
