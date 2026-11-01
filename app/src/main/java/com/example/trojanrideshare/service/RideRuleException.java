package com.example.trojanrideshare.service;

/** A ride rule was broken. The message is written to be shown to the user. */
public class RideRuleException extends RuntimeException {

    public RideRuleException(String message) {
        super(message);
    }
}
