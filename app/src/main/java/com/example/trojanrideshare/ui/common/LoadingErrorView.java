package com.example.trojanrideshare.ui.common;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.trojanrideshare.R;

/**
 * Drives the shared view_loading_error.xml layout.
 * Usage in a Fragment: include the layout with
 * {@code <include android:id="@+id/loading_error" layout="@layout/view_loading_error" />},
 * then {@code new LoadingErrorView(view.findViewById(R.id.loading_error), this::reload)}
 * and call {@link #render(UiState)} whenever the ViewModel's state changes.
 */
public class LoadingErrorView {

    private final View root;
    private final ProgressBar progress;
    private final TextView errorText;
    private final Button retryButton;

    public LoadingErrorView(View root, Runnable onRetry) {
        this.root = root;
        this.progress = root.findViewById(R.id.loading_progress);
        this.errorText = root.findViewById(R.id.error_message);
        this.retryButton = root.findViewById(R.id.retry_button);
        retryButton.setOnClickListener(v -> onRetry.run());
    }

    public void render(UiState state) {
        boolean loading = state.isLoading();
        boolean error = state.isError();
        root.setVisibility(loading || error ? View.VISIBLE : View.GONE);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        errorText.setVisibility(error ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(error ? View.VISIBLE : View.GONE);
        if (error) {
            errorText.setText(state.getMessage());
        }
    }
}
