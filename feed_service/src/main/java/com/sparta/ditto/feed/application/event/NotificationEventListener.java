package com.sparta.ditto.feed.application.event;

import com.sparta.ditto.feed.application.port.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationEventPublisher notificationEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        notificationEventPublisher.publishPostLiked(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommented(PostCommentedEvent event) {
        notificationEventPublisher.publishPostCommented(event);
    }
}