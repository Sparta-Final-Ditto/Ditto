package com.sparta.ditto.feed.application.port;

import com.sparta.ditto.feed.application.event.PostCommentedEvent;
import com.sparta.ditto.feed.application.event.PostLikedEvent;

public interface NotificationEventPublisher {

    void publishPostLiked(PostLikedEvent event);

    void publishPostCommented(PostCommentedEvent event);
}
