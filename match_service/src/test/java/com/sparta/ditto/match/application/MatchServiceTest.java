package com.sparta.ditto.match.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.application.service.MatchService;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// Mockito 사용하겠다는 선언
// 실제 Spring 서버 없이 테스트 가능
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    // 실제 구현체 대신 가짜(Mock) 객체 주입
    @Mock
    private MatchingHistoryRepository matchingHistoryRepository;

    // Mock 객체들을 주입받는 테스트 대상
    @InjectMocks
    private MatchService matchService;

    @Test
    @DisplayName("오늘 이미 매칭한 유저는 예외가 발생한다")
    void createMatch_alreadyMatched_throwsException() {
        // given (준비)
        UUID userId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);

        // 오늘 매칭 이력이 있다고 가짜로 설정
        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.of(MatchingHistory.of(
                        userId,
                        UUID.randomUUID(),
                        0.8f,
                        0.75f,
                        "NONE",
                        false
                )));

        // when & then (실행 + 검증)
        // BusinessException이 발생해야 하고
        // 에러 코드가 ALREADY_MATCHED_TODAY여야 함
        assertThatThrownBy(() -> matchService.createMatch(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.ALREADY_MATCHED_TODAY;
                });
    }

    @Test
    @DisplayName("오늘 매칭 이력이 없으면 매칭이 진행된다")
    void createMatch_noHistory_success() {
        // given
        UUID userId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);

        // 오늘 매칭 이력 없음
        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.empty());

        // 저장 시 그대로 반환
        given(matchingHistoryRepository.save(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        matchService.createMatch(userId, request);

        // then
        // save가 한 번 호출됐는지 검증
        verify(matchingHistoryRepository).save(any());
    }

    @Test
    @DisplayName("오늘 매칭 이력이 없으면 getTodayMatch에서 예외가 발생한다")
    void getTodayMatch_noHistory_throwsException() {
        // given
        UUID userId = UUID.randomUUID();

        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> matchService.getTodayMatch(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.MATCH_NOT_FOUND;
                });
    }

    @Test
    @DisplayName("오늘 매칭 이력이 있으면 getTodayMatch가 DTO를 반환한다")
    void getTodayMatch_hasHistory_returnsDto() {
        // given
        UUID userId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                userId,
                UUID.randomUUID(),
                0.8f,
                0.75f,
                "NONE",
                false
        );

        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.of(history));

        // when
        MatchResponseDto result = matchService.getTodayMatch(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.similarityScore()).isEqualTo(0.8f);
        assertThat(result.finalScore()).isEqualTo(0.75f);
        assertThat(result.status()).isEqualTo("PENDING");
    }
}