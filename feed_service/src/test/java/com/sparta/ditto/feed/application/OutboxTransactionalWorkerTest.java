package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxTransactionalWorkerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxTransactionalWorker worker;

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent("post-events", "POST_LIKED", UUID.randomUUID(), "{}");
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }

    @Test
    @DisplayName("markResult 성공 → PUBLISHED 분기로 라우팅 후 저장")
    void markResult_성공_PUBLISHED_저장() {
        // given
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        // when
        worker.markResult(event.getId(), true);

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("markResult 실패 → retry 증가 분기로 라우팅 후 저장")
    void markResult_실패_retry_저장() {
        // given
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        // when
        worker.markResult(event.getId(), false);

        // then
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outboxEventRepository).save(event);
    }
}
