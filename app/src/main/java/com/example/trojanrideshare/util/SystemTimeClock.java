package com.example.trojanrideshare.util;

/** Default Clock backed by the device time. */
public class SystemTimeClock implements Clock {
    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
