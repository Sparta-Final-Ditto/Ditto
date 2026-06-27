package com.sparta.ditto.feed.application.dto.result;

import com.sparta.ditto.feed.domain.entity.Post;
import java.util.UUID;

public record UpdatePostDisplayResult(
        UUID postId,
        Boolean showLocation,
        String visibility
) {
    public static UpdatePostDisplayResult from(Post post) {
        return new UpdatePostDisplayResult(
                post.getId(),
                post.getShowLocation(),
                post.getVisibility().name()
        );
    }
}
