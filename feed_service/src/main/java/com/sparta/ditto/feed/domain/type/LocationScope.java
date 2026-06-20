package com.sparta.ditto.feed.domain.type;

import com.sparta.ditto.feed.domain.exception.InvalidLocationScopeException;

public enum LocationScope {
    PUBLIC, FOLLOWERS_ONLY, PRIVATE;

    public static LocationScope from(String value) {
        if (value == null) {
            return PUBLIC;
        }
        try {
            return LocationScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidLocationScopeException();
        }
    }
}