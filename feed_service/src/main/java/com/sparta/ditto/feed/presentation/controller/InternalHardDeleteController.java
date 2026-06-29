package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.PostHardDeleteScheduler;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 영구 삭제(hard delete) 수동 트리거 — 테스트/개발 전용.
 *
 * <p>{@code @Profile("local")}로 local 프로파일에서만 빈이 등록되어,
 * 운영(prod) 환경에는 배포되더라도 활성화되지 않는다.
 *
 * <p>30일 스케줄을 기다리지 않고 즉시 hard delete를 검증하기 위한 용도다.
 * 안전을 위해 dryRun 기본값은 true이며, 실제 삭제는 dryRun=false를 명시해야 한다.
 */
@Profile("local")
@RestController
@RequestMapping("/internal/hard-delete")
@RequiredArgsConstructor
public class InternalHardDeleteController {

    private final PostHardDeleteScheduler postHardDeleteScheduler;

    /**
     * @param daysAgo cutoff 기준일. 0이면 "지금"까지의 모든 soft delete 게시글이 대상(즉시 검증용).
     *                기본값은 설정된 보관 기간(retention-days)을 사용한다.
     * @param dryRun  true(기본)면 실제 삭제 없이 대상 ID 목록만 반환. false면 실제 삭제 수행.
     */
    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trigger(
            @RequestParam(required = false) Integer daysAgo,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        int days = (daysAgo != null) ? daysAgo : postHardDeleteScheduler.getRetentionDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        if (dryRun) {
            List<UUID> targets = postHardDeleteScheduler.findHardDeleteTargets(cutoff);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "dryRun", true,
                    "cutoff", cutoff.toString(),
                    "targetCount", targets.size(),
                    "targetPostIds", targets
            )));
        }

        postHardDeleteScheduler.processHardDelete(cutoff);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "dryRun", false,
                "cutoff", cutoff.toString(),
                "message", "hard delete executed"
        )));
    }
}
