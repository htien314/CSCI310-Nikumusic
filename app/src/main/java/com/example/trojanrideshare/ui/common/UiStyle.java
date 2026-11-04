package com.example.trojanrideshare.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.trojanrideshare.R;
import com.google.android.material.button.MaterialButton;

/** Shared native styling for rows created dynamically by the feature screens. */
public final class UiStyle {
    private UiStyle() {}
    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    public static int color(Context context, int resource) { return ContextCompat.getColor(context, resource); }
    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int padding = dp(context, 20);
        card.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 12), 0, dp(context, 4));
        card.setLayoutParams(params);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true))
            card.setForeground(ContextCompat.getDrawable(context, ripple.resourceId));
        return card;
    }
    public static TextView text(Context context, String value, int size, boolean bold, boolean muted) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size);
        view.setTextColor(color(context, muted ? R.color.app_muted : R.color.app_ink));
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }
    public static Button secondaryButton(Context context) {
        MaterialButton button = new MaterialButton(context);
        button.setBackgroundTintList(ColorStateList.valueOf(color(context, R.color.app_surface)));
        button.setTextColor(color(context, R.color.brand_primary));
        button.setStrokeWidth(dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(color(context, R.color.app_outline)));
        return button;
    }
}
