package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.application.service.PostHardDeleteService;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.repository.PostMediaRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게시글 영구 삭제(hard delete) 배치 스케줄러.
 *
 * <p>매일 04:00에 soft delete 후 보관 기간(기본 30일)이 경과한 게시글을 청크 단위로 처리한다.
 * 각 게시글마다 S3 미디어 삭제를 먼저 수행한 뒤 DB 물리 삭제를 호출한다.
 * S3 삭제 실패 시 해당 건의 DB 삭제를 건너뛰어 멱등성을 보장하며,
 * 나머지 건은 계속 처리한다(실패 격리).
 *
 * <p>트랜잭션을 갖지 않는다. DB 물리 삭제 트랜잭션은 {@link PostHardDeleteService}가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class PostHardDeleteScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PostHardDeleteScheduler.class);

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final S3Port s3Port;
    private final PostHardDeleteService postHardDeleteService;

    @Value("${app.post-hard-delete.retention-days}")
    private int retentionDays;

    @Value("${app.post-hard-delete.chunk-size}")
    private int chunkSize;

    @Scheduled(cron = "${app.post-hard-delete.cron}")
    public void scheduledHardDelete() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        processHardDelete(cutoff);
    }

    public void processHardDelete(Instant cutoff) {
        List<Post> expiredPosts = postRepository.findExpiredSoftDeleted(cutoff, chunkSize);
        LOG.info("[HardDelete] 처리 대상 {}건 (cutoff={})", expiredPosts.size(), cutoff);

        for (Post post : expiredPosts) {
            UUID postId = post.getId();
            try {
                List<PostMedia> mediaList =
                        postMediaRepository.findByPostIdOrderBySortOrder(postId);
                List<String> s3Keys = mediaList.stream()
                        .map(PostMedia::getS3Key)
                        .collect(Collectors.toList());

                if (!s3Keys.isEmpty()) {
                    s3Port.deleteObjects(s3Keys);
                }
                postHardDeleteService.purgePost(postId);
            } catch (Exception e) {
                LOG.warn("[HardDelete] postId={} 처리 실패, 다음 주기에 재시도. 원인: {}",
                        postId, e.getMessage());
            }
        }
    }

    /** dry-run 미리보기: 실제 삭제 없이 현재 cutoff 기준 삭제 대상 ID 목록만 반환한다. */
    public List<UUID> findHardDeleteTargets(Instant cutoff) {
        return postRepository.findExpiredSoftDeleted(cutoff, chunkSize).stream()
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    /** 외부 트리거(테스트/관리)용 retentionDays 노출. */
    public int getRetentionDays() {
        return retentionDays;
    }
}
