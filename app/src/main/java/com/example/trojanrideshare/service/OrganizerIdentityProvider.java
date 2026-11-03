package com.example.trojanrideshare.service;

import com.example.trojanrideshare.model.User;
import com.example.trojanrideshare.util.Callback;

/** Resolves the signed-in student's profile before a ride is created. */
public interface OrganizerIdentityProvider {
    void load(Callback<User> callback);
}
