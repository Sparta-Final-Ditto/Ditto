package com.sparta.ditto.feed.application.port;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import java.util.List;
import java.util.UUID;

public interface OutboxEventPort {

    OutboxEvent buildPostCreated(Post post, UUID userId, List<String> tags);

    OutboxEvent buildPostDeleted(Post post, UUID deletedBy);

    OutboxEvent buildPostHardDeleted(Post post, UUID deletedBy);

    OutboxEvent buildPostRestored(Post post, UUID restoredBy);
}
