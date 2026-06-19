package com.sparta.ditto.feed.domain.port;

import java.util.UUID;

public interface UserPort {
    String getNickname(UUID userId);
}