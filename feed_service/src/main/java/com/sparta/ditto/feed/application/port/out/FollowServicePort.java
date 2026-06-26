package com.sparta.ditto.feed.application.port.out;

import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import java.util.UUID;

public interface FollowServicePort {
    FollowingResult getFollowingIds(UUID userId);
}