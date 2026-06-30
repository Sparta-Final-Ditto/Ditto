package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status, int limit);

    List<OutboxEvent> findPendingForUpdate(OutboxStatus status, int limit);

    long countByStatus(OutboxStatus status);
}
