package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.application.service.PostHardDeleteService;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.repository.PostMediaRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostHardDeleteScheduler 단위 테스트.
 * 스케줄러의 조율 로직(S3→DB 순서, 실패 격리, 멱등성)을 순수 Mockito로 검증한다.
 * @Scheduled 트리거를 기다리지 않고 processHardDelete(cutoff)를 직접 호출한다.
 */
@ExtendWith(MockitoExtension.class)
class PostHardDeleteSchedulerTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostMediaRepository postMediaRepository;

    @Mock
    private S3Port s3Port;

    @Mock
    private PostHardDeleteService postHardDeleteService;

    @InjectMocks
    private PostHardDeleteScheduler scheduler;

    private final Instant cutoff = Instant.parse("2025-05-01T04:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
        ReflectionTestUtils.setField(scheduler, "chunkSize", 100);
    }

    private Post mockPost(UUID id) {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(id);
        return post;
    }

    private PostMedia mockMedia(String s3Key) {
        PostMedia media = mock(PostMedia.class);
        when(media.getS3Key()).thenReturn(s3Key);
        return media;
    }

    @Test
    @DisplayName("S3 삭제 후 DB 삭제 순서 보장 — InOrder로 s3Port.deleteObjects → purgePost 검증")
    void processHardDelete_S3삭제후_DB삭제_순서보장() {
        // given
        UUID postId = UUID.randomUUID();
        Post post = mockPost(postId);
        PostMedia media = mockMedia("feeds/test-image.jpg");

        when(postRepository.findExpiredSoftDeleted(cutoff, 100)).thenReturn(List.of(post));
        when(postMediaRepository.findByPostIdOrderBySortOrder(postId)).thenReturn(List.of(media));

        // when
        scheduler.processHardDelete(cutoff);

        // then
        InOrder inOrder = inOrder(s3Port, postHardDeleteService);
        inOrder.verify(s3Port).deleteObjects(List.of("feeds/test-image.jpg"));
        inOrder.verify(postHardDeleteService).purgePost(postId);
    }

    @Test
    @DisplayName("S3 삭제 실패 시 해당 건의 purgePost 미호출 — soft delete 상태 유지 → 다음 주기 재시도")
    void processHardDelete_S3실패시_해당건_purgePost_미호출() {
        // given
        UUID postId = UUID.randomUUID();
        Post post = mockPost(postId);
        PostMedia media = mockMedia("feeds/fail-image.jpg");

        when(postRepository.findExpiredSoftDeleted(cutoff, 100)).thenReturn(List.of(post));
        when(postMediaRepository.findByPostIdOrderBySortOrder(postId)).thenReturn(List.of(media));
        doThrow(new RuntimeException("S3 연결 실패")).when(s3Port).deleteObjects(anyList());

        // when
        scheduler.processHardDelete(cutoff);

        // then: S3 실패한 건은 DB 삭제를 수행하지 않음
        verify(postHardDeleteService, never()).purgePost(any(UUID.class));
    }

    @Test
    @DisplayName("3건 중 1건 S3 실패 시 나머지 2건은 purgePost 정상 호출 — 실패 격리 보장")
    void processHardDelete_일부S3실패시_나머지건_정상처리() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Post p1 = mockPost(id1);
        Post p2 = mockPost(id2);
        Post p3 = mockPost(id3);

        PostMedia m1 = mockMedia("feeds/s3key-1.jpg");
        PostMedia m2 = mockMedia("feeds/s3key-2.jpg");  // S3 실패 대상
        PostMedia m3 = mockMedia("feeds/s3key-3.jpg");

        when(postRepository.findExpiredSoftDeleted(cutoff, 100))
                .thenReturn(List.of(p1, p2, p3));
        when(postMediaRepository.findByPostIdOrderBySortOrder(id1)).thenReturn(List.of(m1));
        when(postMediaRepository.findByPostIdOrderBySortOrder(id2)).thenReturn(List.of(m2));
        when(postMediaRepository.findByPostIdOrderBySortOrder(id3)).thenReturn(List.of(m3));

        // id2의 s3Key만 S3 삭제 실패
        doAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            if (keys.contains("feeds/s3key-2.jpg")) {
                throw new RuntimeException("S3 부분 실패");
            }
            return null;
        }).when(s3Port).deleteObjects(anyList());

        // when
        scheduler.processHardDelete(cutoff);

        // then: id1, id3는 purgePost 호출 / id2는 미호출
        verify(postHardDeleteService).purgePost(eq(id1));
        verify(postHardDeleteService, never()).purgePost(eq(id2));
        verify(postHardDeleteService).purgePost(eq(id3));
    }
}
