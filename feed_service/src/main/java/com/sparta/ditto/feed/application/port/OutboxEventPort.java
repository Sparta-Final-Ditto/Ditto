package com.sparta.ditto.feed.application.port;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import java.util.List;
import java.util.UUID;

public interface OutboxEventPort {

    OutboxEvent buildPostLiked(Post post, UUID likerId);

    OutboxEvent buildPostCreated(Post post, UUID userId, List<String> tags);

    OutboxEvent buildPostCommented(Post post, Comment comment, UUID commenterId);
}
