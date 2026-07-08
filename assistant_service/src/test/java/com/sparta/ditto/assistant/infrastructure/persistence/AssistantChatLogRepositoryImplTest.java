package com.sparta.ditto.assistant.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.common.util.UuidV7Generator;
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

    @Mock
    private UuidV7Generator uuidV7Generator;

    @InjectMocks
    private AssistantChatLogRepositoryImpl repositoryImpl;

    @Test
    @DisplayName("save()는 v7 id를 생성해 도메인 엔티티를 JPA 엔티티로 변환해 저장하고, 결과를 도메인 엔티티로 반환한다")
    void save_mapsToJpaEntityAndBackToDomain() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID generatedId = UUID.randomUUID();
        AssistantChatLog chatLog = AssistantChatLog.of(
                userId, "질문", "답변", List.of(documentId), List.of(0.9f));
        given(uuidV7Generator.generate()).willReturn(generatedId);
        AssistantChatLogJpaEntity savedEntity =
                AssistantChatLogJpaEntity.from(chatLog, generatedId);
        given(jpaRepository.save(any(AssistantChatLogJpaEntity.class))).willReturn(savedEntity);

        AssistantChatLog result = repositoryImpl.save(chatLog);

        assertThat(result.getId()).isEqualTo(generatedId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getQuestion()).isEqualTo("질문");
        assertThat(result.getMatchedDocumentIds()).containsExactly(documentId);
        verify(jpaRepository).save(any(AssistantChatLogJpaEntity.class));
    }
}
