package com.sparta.ditto.assistant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantChatLogRepositoryImplTest {

    @Mock
    private AssistantChatLogJpaRepository jpaRepository;

    @InjectMocks
    private AssistantChatLogRepositoryImpl repositoryImpl;

    @Test
    @DisplayName("save()는 JPA repository의 save를 위임 호출한다")
    void save_delegatesToJpaRepository() {
        AssistantChatLog chatLog = AssistantChatLog.of(
                UUID.randomUUID(), "질문", "답변", List.of(UUID.randomUUID()), List.of(0.9f));
        given(jpaRepository.save(chatLog)).willReturn(chatLog);

        AssistantChatLog result = repositoryImpl.save(chatLog);

        assertThat(result).isEqualTo(chatLog);
        verify(jpaRepository).save(chatLog);
    }
}
