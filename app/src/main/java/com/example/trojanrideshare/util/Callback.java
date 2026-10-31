package com.example.trojanrideshare.util;

/**
 * Result callback used by every repository and service method.
 * Firestore calls are asynchronous, so methods report back through this
 * instead of returning a value.
 */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(Exception e);
}
