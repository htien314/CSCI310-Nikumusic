package com.example.trojanrideshare.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.model.Location;
import com.example.trojanrideshare.service.LocationGateway;
import com.example.trojanrideshare.util.Callback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Shared place input (owner: Nikhil). The user types a place and taps the search icon or the keyboard's
 * search key; the view looks it up through a {@link LocationGateway} and, if several places match,
 * asks which one. The chosen place has an address plus latitude/longitude.
 *
 * <pre>
 * &lt;com.example.trojanrideshare.ui.common.LocationPickerView
 *     android:id="@+id/origin_picker"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:hint="Origin" /&gt;
 *
 * originPicker.setGateway(new GeocoderLocationGateway(requireContext()));
 * originPicker.resolve(callback); // on submit: the chosen place, or a lookup of the typed text
 * </pre>
 *
 * Give each picker its own android:id so its text and chosen place survive rotation.
 */
public class LocationPickerView extends LinearLayout {

    /** Called with the chosen place, or with null when the user edits the text and the choice no longer applies. */
    public interface OnLocationSelectedListener {
        void onLocationSelected(@Nullable Location location);
    }

    private static final int MAX_CHOICES = 5;

    private final TextInputLayout inputLayout;
    private final TextInputEditText input;

    @Nullable
    private LocationGateway gateway;
    @Nullable
    private Location selected;
    @Nullable
    private OnLocationSelectedListener listener;
    private boolean settingText;
    // Only the newest lookup's answer is used, so a slow old answer can't overwrite a newer one.
    private int lookupGeneration;

    public LocationPickerView(Context context) {
        this(context, null);
    }

    public LocationPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);

        CharSequence hint = null;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.hint});
            hint = a.getText(0);
            a.recycle();
        }

        inputLayout = new TextInputLayout(context);
        inputLayout.setHint(hint);
        inputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        inputLayout.setEndIconDrawable(android.R.drawable.ic_menu_search);
        inputLayout.setEndIconContentDescription(R.string.location_find);
        inputLayout.setEndIconOnClickListener(v -> findMatches());

        input = new TextInputEditText(inputLayout.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        input.setMaxLines(1);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        // This view saves the text itself; see onSaveInstanceState.
        input.setSaveEnabled(false);
        inputLayout.addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(inputLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                findMatches();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (settingText) {
                    return;
                }
                inputLayout.setError(null);
                if (selected != null) {
                    selected = null;
                    inputLayout.setHelperText(null);
                    notifyListener();
                }
            }
        });
    }

    public void setGateway(@Nullable LocationGateway gateway) {
        this.gateway = gateway;
    }

    public void setOnLocationSelectedListener(@Nullable OnLocationSelectedListener listener) {
        this.listener = listener;
    }

    /** The place the user chose, or null if they haven't chosen one (or edited the text since). */
    @Nullable
    public Location getSelectedLocation() {
        return selected;
    }

    /** What's typed in the box, trimmed. */
    @NonNull
    public String getText() {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    /** Shows a place as already chosen, e.g. when editing a saved one. Null clears the box. */
    public void setLocation(@Nullable Location location) {
        if (location == null) {
            clear();
        } else {
            select(location);
        }
    }

    public void clear() {
        lookupGeneration++;
        settingText = true;
        input.setText(null);
        settingText = false;
        selected = null;
        inputLayout.setError(null);
        inputLayout.setHelperText(null);
        notifyListener();
    }

    /** Looks up the typed text; picks the place right away if there's one match, otherwise asks the user. */
    public void findMatches() {
        String query = getText();
        if (query.isEmpty()) {
            inputLayout.setError(getContext().getString(R.string.location_enter));
            return;
        }
        if (gateway == null) {
            return;
        }
        int generation = ++lookupGeneration;
        inputLayout.setError(null);
        inputLayout.setHelperText(getContext().getString(R.string.location_searching));
        gateway.suggest(query, MAX_CHOICES, new Callback<List<Location>>() {
            @Override
            public void onSuccess(List<Location> places) {
                if (generation != lookupGeneration) {
                    return;
                }
                inputLayout.setHelperText(null);
                if (places.size() == 1) {
                    select(places.get(0));
                } else {
                    askWhich(places);
                }
            }

            @Override
            public void onError(Exception e) {
                if (generation == lookupGeneration) {
                    showError(e);
                }
            }
        });
    }

    /**
     * For forms: reports the chosen place, or looks up the typed text if nothing has been chosen yet,
     * using the best match. Errors are shown on the field and also passed to the callback.
     */
    public void resolve(Callback<Location> callback) {
        if (selected != null) {
            callback.onSuccess(selected);
            return;
        }
        String query = getText();
        if (query.isEmpty()) {
            String message = getContext().getString(R.string.location_enter);
            inputLayout.setError(message);
            callback.onError(new IllegalArgumentException(message));
            return;
        }
        if (gateway == null) {
            callback.onError(new IllegalStateException("No location service set."));
            return;
        }
        int generation = ++lookupGeneration;
        gateway.lookup(query, new Callback<Location>() {
            @Override
            public void onSuccess(Location place) {
                if (generation == lookupGeneration) {
                    select(place);
                }
                callback.onSuccess(place);
            }

            @Override
            public void onError(Exception e) {
                if (generation == lookupGeneration) {
                    showError(e);
                }
                callback.onError(e);
            }
        });
    }

    private void askWhich(List<Location> places) {
        CharSequence[] addresses = new CharSequence[places.size()];
        for (int i = 0; i < places.size(); i++) {
            addresses[i] = places.get(i).getAddress();
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.location_choose)
                .setItems(addresses, (dialog, which) -> select(places.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void select(Location place) {
        settingText = true;
        input.setText(place.getAddress());
        if (input.getText() != null) {
            input.setSelection(input.getText().length());
        }
        settingText = false;
        selected = place;
        inputLayout.setError(null);
        inputLayout.setHelperText(getContext().getString(R.string.location_selected));
        notifyListener();
    }

    private void showError(Exception e) {
        inputLayout.setHelperText(null);
        inputLayout.setError(e.getMessage() != null
                ? e.getMessage() : getContext().getString(R.string.location_failed));
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onLocationSelected(selected);
        }
    }

    // ---- saving state across rotation ----

    private static final String STATE_SUPER = "super";
    private static final String STATE_TEXT = "text";
    private static final String STATE_ADDRESS = "address";
    private static final String STATE_LAT = "lat";
    private static final String STATE_LNG = "lng";

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable(STATE_SUPER, super.onSaveInstanceState());
        state.putString(STATE_TEXT, getText());
        if (selected != null) {
            state.putString(STATE_ADDRESS, selected.getAddress());
            state.putDouble(STATE_LAT, selected.getLatitude());
            state.putDouble(STATE_LNG, selected.getLongitude());
        }
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof Bundle)) {
            super.onRestoreInstanceState(state);
            return;
        }
        Bundle bundle = (Bundle) state;
        super.onRestoreInstanceState(bundle.getParcelable(STATE_SUPER));
        settingText = true;
        input.setText(bundle.getString(STATE_TEXT));
        settingText = false;
        String address = bundle.getString(STATE_ADDRESS);
        if (address != null) {
            select(new Location(address, bundle.getDouble(STATE_LAT), bundle.getDouble(STATE_LNG)));
        }
    }

    // Children don't save their own state, so two pickers on one screen don't overwrite each other.
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }
}
