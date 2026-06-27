package com.sparta.ditto.feed.domain.type;

import com.sparta.ditto.feed.domain.exception.InvalidVisibilityException;

public enum Visibility {
    PUBLIC, FOLLOWERS_ONLY, PRIVATE;

    public static Visibility from(String value) {
        if (value == null) {
            return PUBLIC;
        }
        try {
            return Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidVisibilityException();
        }
    }
}
