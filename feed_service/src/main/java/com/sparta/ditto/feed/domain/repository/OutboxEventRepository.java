package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID id);

    List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status, int limit);

    List<OutboxEvent> findPendingForUpdate(OutboxStatus status, int limit);

    long countByStatus(OutboxStatus status);
}
