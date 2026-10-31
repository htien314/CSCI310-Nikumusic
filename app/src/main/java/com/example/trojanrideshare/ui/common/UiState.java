package com.example.trojanrideshare.ui.common;

import androidx.annotation.Nullable;

/** Loading / success / error state that every ViewModel exposes to its screen. */
public final class UiState {

    public enum Status { IDLE, LOADING, SUCCESS, ERROR }

    private final Status status;
    @Nullable
    private final String message;

    private UiState(Status status, @Nullable String message) {
        this.status = status;
        this.message = message;
    }

    public static UiState idle() {
        return new UiState(Status.IDLE, null);
    }

    public static UiState loading() {
        return new UiState(Status.LOADING, null);
    }

    public static UiState success() {
        return new UiState(Status.SUCCESS, null);
    }

    public static UiState success(String message) {
        return new UiState(Status.SUCCESS, message);
    }

    public static UiState error(String message) {
        return new UiState(Status.ERROR, message);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
