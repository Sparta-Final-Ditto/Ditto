package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPort outboxEventPort;

    @InjectMocks
    private PostService postService;

    private final UUID userId = UUID.randomUUID();
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);

        lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(post, "createdAt", Instant.now());
            return post;
        });
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(outboxEventPort.buildPostCreated(any(Post.class), any(UUID.class), any()))
                .thenReturn(new OutboxEvent("post-events", "POST_CREATED", "{}"));
    }

    private CreatePostCommand defaultCommand() {
        return new CreatePostCommand(
                userId, "새벽러너",
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563,
                127.0374,
                "PUBLIC",
                true,
                List.of(new MediaFileItem("feeds/test-uuid.mp4", "VIDEO", 1))
        );
    }

    @Test
    @DisplayName("필수 필드와 content 전달 → 201, 게시글 생성")
    void 필수필드와_content_전달_게시글생성() {
        // when
        PostResult result = postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        assertThat(result.postId()).isNotNull();
        assertThat(result.content()).isEqualTo("오늘 새벽 러닝 완료!");
    }

    @Test
    @DisplayName("content=null, mediaFiles 1개 이상 → 201, 게시글 생성")
    void content_null_mediaFiles_존재_게시글생성() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", null, List.of("#러닝"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileItem("feeds/test.mp4", "VIDEO", 1))
        );

        // when
        PostResult result = postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        assertThat(result.postId()).isNotNull();
        assertThat(result.content()).isNull();
    }

    @Test
    @DisplayName("mediaFiles=[], content 존재 → 201, 게시글 생성")
    void mediaFiles_비어있고_content_존재_게시글생성() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "텍스트만 있는 게시글", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of()
        );

        // when
        PostResult result = postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        assertThat(result.postId()).isNotNull();
        assertThat(result.mediaFiles()).isEmpty();
    }

    @Test
    @DisplayName("모든 필드 전달 → 201, 모든 필드 반영")
    void 모든필드_전달_모든필드_반영() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너",
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563, 127.0374,
                "FOLLOWERS_ONLY", false,
                List.of(new MediaFileItem("feeds/test.mp4", "VIDEO", 1))
        );

        // when
        PostResult result = postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        assertThat(result.content()).isEqualTo("오늘 새벽 러닝 완료!");
        assertThat(result.tags()).containsExactlyInAnyOrder("#새벽운동", "#러닝");
        assertThat(result.showLocation()).isFalse();
        assertThat(result.neighborhood()).isNull(); // showLocation=false이면 neighborhood=null 반환 (DATA_MODEL)
        assertThat(result.likeCount()).isZero();
        assertThat(result.isLiked()).isFalse();
        assertThat(result.commentCount()).isZero();
    }

    @Test
    @DisplayName("locationScope 누락 → PUBLIC으로 저장")
    void locationScope_누락_PUBLIC_기본값() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "공개 범위 없는 게시글", List.of("#태그"),
                37.5563, 127.0374,
                null, true, List.of()
        );
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getLocationScope().name()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("showLocation 누락 → true로 저장")
    void showLocation_누락_true_기본값() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "showLocation 없는 게시글", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", null, List.of()
        );
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getShowLocation()).isTrue();
    }

    @Test
    @DisplayName("locationScope=INVALID → 400, VALIDATION_ERROR 공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.")
    void locationScope_유효하지않음_INVALID_LOCATION_SCOPE() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#태그"),
                37.5563, 127.0374,
                "INVALID", true, List.of()
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.");
                });
    }

    @Test
    @DisplayName("mediaType=FILE → 400, VALIDATION_ERROR 미디어 타입은 IMAGE, VIDEO 중 선택해주세요.")
    void mediaType_FILE_INVALID_POST_MEDIA_TYPE() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileItem("feeds/test.pdf", "FILE", 1))
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("미디어 타입은 IMAGE, VIDEO 중 선택해주세요.");
                });
    }

    @Test
    @DisplayName("전달받은 neighborhood 값이 응답에 반영")
    void neighborhood_값_응답에_반영() {
        // when
        PostResult result = postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        assertThat(result.neighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("전달받은 neighborhood 결과가 Post에 저장됨")
    void neighborhood_결과_Post에_저장() {
        // given
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getNeighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("neighborhood 값이 null인 경우 응답에 null로 설정")
    void neighborhood_null_응답_게시글생성() {
        // when
        PostResult result = postService.createPost(defaultCommand(), null, "새벽러너");

        // then
        assertThat(result.postId()).isNotNull();
        assertThat(result.neighborhood()).isNull();
    }

    @Test
    @DisplayName("정상 생성 → 응답에 latitude, longitude 필드 없음")
    void 응답에_latitude_longitude_필드_없음() {
        // when
        var componentNames = Arrays.stream(PostResult.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList();

        // then
        assertThat(componentNames).doesNotContain("latitude", "longitude");
    }

    @Test
    @DisplayName("미디어 포함 → mediaUrl이 CloudFront URL로 반환")
    void 미디어포함_CloudFront_mediaUrl_반환() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "미디어 포함 게시글", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileItem("feeds/test-uuid.mp4", "VIDEO", 1))
        );

        // when
        PostResult result = postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        assertThat(result.mediaFiles()).hasSize(1);
        assertThat(result.mediaFiles().get(0).s3Key()).isEqualTo("feeds/test-uuid.mp4");
        assertThat(result.mediaFiles().get(0).mediaUrl())
                .isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/test-uuid.mp4");
        assertThat(result.mediaFiles().get(0).mediaType()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("정상 생성 → 응답에 createdAt 포함")
    void 정상생성_createdAt_포함() {
        // when
        PostResult result = postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("정상 생성 → POST_CREATED OutboxEvent PENDING 상태로 저장")
    void 정상생성_POST_CREATED_Outbox_저장() {
        // given
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        // when
        postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("post-events");
        assertThat(captor.getValue().getEventType()).isEqualTo("POST_CREATED");
        assertThat(captor.getValue().getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("content 비어 있고 mediaFiles=[] → 400, VALIDATION_ERROR 이미지, 영상, 텍스트 중 하나는 반드시 입력해주세요.")
    void content_비어있고_mediaFiles_비어있음_EMPTY_POST() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("이미지, 영상, 텍스트 중 하나는 반드시 입력해주세요.");
                });
    }

    @Test
    @DisplayName("mediaFiles 내 sortOrder 중복 → 400, VALIDATION_ERROR")
    void sortOrder_중복_예외() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileItem("feeds/img1.jpg", "IMAGE", 1),
                        new MediaFileItem("feeds/img2.jpg", "IMAGE", 1)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("tags 내 중복 태그 포함 → 201, 중복 제거 후 저장")
    void 중복태그_중복제거_저장() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#러닝", "#러닝", "#새벽"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        // when
        PostResult result = postService.createPost(command, "서울 성동구", "새벽러너");

        // then
        assertThat(result.tags()).containsExactlyInAnyOrder("#러닝", "#새벽");
        assertThat(result.tags()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이미지 6장 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void 이미지6장_미디어개수초과_예외() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileItem("feeds/1.jpg", "IMAGE", 1),
                        new MediaFileItem("feeds/2.jpg", "IMAGE", 2),
                        new MediaFileItem("feeds/3.jpg", "IMAGE", 3),
                        new MediaFileItem("feeds/4.jpg", "IMAGE", 4),
                        new MediaFileItem("feeds/5.jpg", "IMAGE", 5),
                        new MediaFileItem("feeds/6.jpg", "IMAGE", 6)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("영상 2개 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void 영상2개_미디어개수초과_예외() {
        // given
        CreatePostCommand command = new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileItem("feeds/1.mp4", "VIDEO", 1),
                        new MediaFileItem("feeds/2.mp4", "VIDEO", 2)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(command, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("정상 생성 → 응답에 author.userId, author.nickname 포함")
    void 정상생성_author_포함() {
        // when
        PostResult result = postService.createPost(defaultCommand(), "서울 성동구", "새벽러너");

        // then
        assertThat(result.authorUserId()).isEqualTo(userId);
        assertThat(result.authorNickname()).isEqualTo("새벽러너");
    }

    @Test
    @DisplayName("user-service 호출 실패 → 닉네임 null 전달 시 null 반영")
    void userService_실패_nickname_null_게시글생성() {
        // when
        PostResult result = postService.createPost(defaultCommand(), "서울 성동구", null);

        // then
        assertThat(result.postId()).isNotNull();
        assertThat(result.authorUserId()).isEqualTo(userId);
        assertThat(result.authorNickname()).isNull();
    }

    // ============================================================
    // DELETE /posts/{postId} (게시글 삭제) — TC-FEED-API-015
    // ============================================================

    private Post createExistingPost(UUID ownerId) {
        Post post = new Post(ownerId, "닉네임", "내용", "서울 성동구",
                37.5563, 127.0374, LocationScope.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        return post;
    }

    @Test
    @DisplayName("TC-015-1: 작성자가 자신의 게시글 삭제 → 성공")
    void deletePost_작성자_삭제_성공() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(requesterId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(outboxEventPort.buildPostDeleted(any(Post.class), any(UUID.class)))
                .thenReturn(new OutboxEvent("post-events", "POST_DELETED", "{}"));

        // when & then
        assertThatCode(() -> postService.deletePost(postId, requesterId, "USER"))
                .doesNotThrowAnyException();

        verify(commentRepository).softDeleteAllByPostId(postId, requesterId);
        verify(likeRepository).softDeleteAllByPostId(postId, requesterId);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("TC-015-3: 없는 게시글 삭제 → 404 POST_NOT_FOUND")
    void deletePost_없는게시글_PostNotFoundException() {
        // given
        UUID postId = UUID.randomUUID();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.deletePost(postId, UUID.randomUUID(), "USER"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-015-4: 이미 삭제된 게시글 삭제 → 404 POST_NOT_FOUND")
    void deletePost_이미삭제된게시글_PostNotFoundException() {
        // given: findByIdAndDeletedAtIsNull은 삭제된 게시글에 대해 empty를 반환
        UUID postId = UUID.randomUUID();
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.deletePost(postId, UUID.randomUUID(), "USER"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-015-2/10: 작성자 아닌 USER가 게시글 삭제 → 403 FORBIDDEN")
    void deletePost_타인USER_ForbiddenException() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(ownerId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> postService.deletePost(postId, strangerId, "USER"))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).softDeleteAllByPostId(any(), any());
        verify(likeRepository, never()).softDeleteAllByPostId(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-015-9: ADMIN이 타인 게시글 삭제 → 성공")
    void deletePost_ADMIN_타인게시글_삭제_성공() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(ownerId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(outboxEventPort.buildPostDeleted(any(Post.class), any(UUID.class)))
                .thenReturn(new OutboxEvent("post-events", "POST_DELETED", "{}"));

        // when & then
        assertThatCode(() -> postService.deletePost(postId, adminId, "ADMIN"))
                .doesNotThrowAnyException();

        verify(commentRepository).softDeleteAllByPostId(postId, adminId);
        verify(likeRepository).softDeleteAllByPostId(postId, adminId);
    }

    @Test
    @DisplayName("TC-015-5: 게시글 삭제 시 댓글 일괄 소프트 삭제 호출")
    void deletePost_댓글_cascade_softDelete_호출() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(requesterId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(outboxEventPort.buildPostDeleted(any(Post.class), any(UUID.class)))
                .thenReturn(new OutboxEvent("post-events", "POST_DELETED", "{}"));

        // when
        postService.deletePost(postId, requesterId, "USER");

        // then
        verify(commentRepository).softDeleteAllByPostId(postId, requesterId);
    }

    @Test
    @DisplayName("TC-015-6: 게시글 삭제 시 좋아요 일괄 소프트 삭제 호출")
    void deletePost_좋아요_cascade_softDelete_호출() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(requesterId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(outboxEventPort.buildPostDeleted(any(Post.class), any(UUID.class)))
                .thenReturn(new OutboxEvent("post-events", "POST_DELETED", "{}"));

        // when
        postService.deletePost(postId, requesterId, "USER");

        // then
        verify(likeRepository).softDeleteAllByPostId(postId, requesterId);
    }

    @Test
    @DisplayName("TC-015-7: 게시글 삭제 시 POST_DELETED Outbox 이벤트 저장")
    void deletePost_POST_DELETED_outbox_저장() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(requesterId);
        ReflectionTestUtils.setField(post, "id", postId);
        OutboxEvent expectedEvent = new OutboxEvent("post-events", "POST_DELETED", "{}");

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(outboxEventPort.buildPostDeleted(any(Post.class), eq(requesterId)))
                .thenReturn(expectedEvent);

        // when
        postService.deletePost(postId, requesterId, "USER");

        // then
        verify(outboxEventPort).buildPostDeleted(any(Post.class), eq(requesterId));
        verify(outboxEventRepository).save(expectedEvent);
    }

    @Test
    @DisplayName("TC-015-8: 댓글 cascade 실패 시 Outbox 이벤트 저장 안됨")
    void deletePost_cascade_실패시_outbox_미저장() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post post = createExistingPost(requesterId);
        ReflectionTestUtils.setField(post, "id", postId);

        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        doThrow(new RuntimeException("DB error")).when(commentRepository)
                .softDeleteAllByPostId(postId, requesterId);

        // when & then
        assertThatThrownBy(() -> postService.deletePost(postId, requesterId, "USER"))
                .isInstanceOf(RuntimeException.class);

        verify(outboxEventRepository, never()).save(any());
        verify(likeRepository, never()).softDeleteAllByPostId(any(), any());
    }
}
