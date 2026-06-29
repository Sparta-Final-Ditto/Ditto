package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findByStatusOrderByCreatedAt(OutboxStatus status, int limit) {
        return jpaRepository.findByStatus(status, PageRequest.of(0, limit));
    }

    @Override
    public List<OutboxEvent> findPendingForUpdate(OutboxStatus status, int limit) {
        return jpaRepository.findPendingForUpdate(status, PageRequest.of(0, limit));
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
