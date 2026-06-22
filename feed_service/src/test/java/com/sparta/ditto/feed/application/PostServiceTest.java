package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

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

    private CreatePostRequest defaultRequest() {
        return new CreatePostRequest(
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563,
                127.0374,
                "PUBLIC",
                true,
                List.of(new MediaFileRequest("feeds/test-uuid.mp4", "VIDEO", 1))
        );
    }

    @Test
    @DisplayName("필수 필드와 content 전달 → 201, 게시글 생성")
    void 필수필드와_content_전달_게시글생성() {
        // given
        CreatePostRequest request = defaultRequest();

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.postId()).isNotNull();
        assertThat(response.content()).isEqualTo("오늘 새벽 러닝 완료!");
    }

    @Test
    @DisplayName("content=null, mediaFiles 1개 이상 → 201, 게시글 생성")
    void content_null_mediaFiles_존재_게시글생성() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                null,
                List.of("#러닝"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test.mp4", "VIDEO", 1))
        );

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.postId()).isNotNull();
        assertThat(response.content()).isNull();
    }

    @Test
    @DisplayName("mediaFiles=[], content 존재 → 201, 게시글 생성")
    void mediaFiles_비어있고_content_존재_게시글생성() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "텍스트만 있는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of()
        );

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.postId()).isNotNull();
        assertThat(response.mediaFiles()).isEmpty();
    }

    @Test
    @DisplayName("모든 필드 전달 → 201, 모든 필드 반영")
    void 모든필드_전달_모든필드_반영() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563, 127.0374,
                "FOLLOWERS_ONLY", false,
                List.of(new MediaFileRequest("feeds/test.mp4", "VIDEO", 1))
        );

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.content()).isEqualTo("오늘 새벽 러닝 완료!");
        assertThat(response.tags()).containsExactlyInAnyOrder("#새벽운동", "#러닝");
        assertThat(response.showLocation()).isFalse();
        assertThat(response.neighborhood()).isNull(); // showLocation=false이면 neighborhood=null 반환 (DATA_MODEL)
        assertThat(response.likeCount()).isZero();
        assertThat(response.isLiked()).isFalse();
        assertThat(response.commentCount()).isZero();
    }

    @Test
    @DisplayName("locationScope 누락 → PUBLIC으로 저장")
    void locationScope_누락_PUBLIC_기본값() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "공개 범위 없는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                null, true,
                List.of()
        );
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getLocationScope().name()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("showLocation 누락 → true로 저장")
    void showLocation_누락_true_기본값() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "showLocation 없는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", null,
                List.of()
        );
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getShowLocation()).isTrue();
    }

    @Test
    @DisplayName("locationScope=INVALID → 400, VALIDATION_ERROR 공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.")
    void locationScope_유효하지않음_INVALID_LOCATION_SCOPE() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "INVALID", true, List.of()
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
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
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test.pdf", "FILE", 1))
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
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
        CreatePostResponse response = postService.createPost(userId, defaultRequest(), "서울 성동구", "새벽러너");

        // then
        assertThat(response.neighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("전달받은 neighborhood 결과가 Post에 저장됨")
    void neighborhood_결과_Post에_저장() {
        // given
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);

        // when
        postService.createPost(userId, defaultRequest(), "서울 성동구", "새벽러너");

        // then
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getNeighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("neighborhood 값이 null인 경우 응답에 null로 설정")
    void neighborhood_null_응답_게시글생성() {
        // when
        CreatePostResponse response = postService.createPost(userId, defaultRequest(), null, "새벽러너");

        // then
        assertThat(response.postId()).isNotNull();
        assertThat(response.neighborhood()).isNull();
    }

    @Test
    @DisplayName("정상 생성 → 응답에 latitude, longitude 필드 없음")
    void 응답에_latitude_longitude_필드_없음() {
        // when
        var componentNames = Arrays.stream(CreatePostResponse.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList();

        // then
        assertThat(componentNames).doesNotContain("latitude", "longitude");
    }

    @Test
    @DisplayName("미디어 포함 → mediaUrl이 CloudFront URL로 반환")
    void 미디어포함_CloudFront_mediaUrl_반환() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "미디어 포함 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test-uuid.mp4", "VIDEO", 1))
        );

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.mediaFiles()).hasSize(1);
        assertThat(response.mediaFiles().get(0).s3Key()).isEqualTo("feeds/test-uuid.mp4");
        assertThat(response.mediaFiles().get(0).mediaUrl())
                .isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/test-uuid.mp4");
        assertThat(response.mediaFiles().get(0).mediaType()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("정상 생성 → 응답에 createdAt 포함")
    void 정상생성_createdAt_포함() {
        // when
        CreatePostResponse response = postService.createPost(userId, defaultRequest(), "서울 성동구", "새벽러너");

        // then
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("정상 생성 → POST_CREATED OutboxEvent PENDING 상태로 저장")
    void 정상생성_POST_CREATED_Outbox_저장() {
        // given
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        // when
        postService.createPost(userId, defaultRequest(), "서울 성동구", "새벽러너");

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
        CreatePostRequest request = new CreatePostRequest(
                "", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
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
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileRequest("feeds/img1.jpg", "IMAGE", 1),
                        new MediaFileRequest("feeds/img2.jpg", "IMAGE", 1)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("tags 내 중복 태그 포함 → 201, 중복 제거 후 저장")
    void 중복태그_중복제거_저장() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "내용",
                List.of("#러닝", "#러닝", "#새벽"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        // when
        CreatePostResponse response = postService.createPost(userId, request, "서울 성동구", "새벽러너");

        // then
        assertThat(response.tags()).containsExactlyInAnyOrder("#러닝", "#새벽");
        assertThat(response.tags()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이미지 6장 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void 이미지6장_미디어개수초과_예외() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileRequest("feeds/1.jpg", "IMAGE", 1),
                        new MediaFileRequest("feeds/2.jpg", "IMAGE", 2),
                        new MediaFileRequest("feeds/3.jpg", "IMAGE", 3),
                        new MediaFileRequest("feeds/4.jpg", "IMAGE", 4),
                        new MediaFileRequest("feeds/5.jpg", "IMAGE", 5),
                        new MediaFileRequest("feeds/6.jpg", "IMAGE", 6)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("영상 2개 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void 영상2개_미디어개수초과_예외() {
        // given
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileRequest("feeds/1.mp4", "VIDEO", 1),
                        new MediaFileRequest("feeds/2.mp4", "VIDEO", 2)
                )
        );

        // when & then
        assertThatThrownBy(() -> postService.createPost(userId, request, "서울 성동구", "새벽러너"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("정상 생성 → 응답에 author.userId, author.nickname 포함")
    void 정상생성_author_포함() {
        // when
        CreatePostResponse response = postService.createPost(userId, defaultRequest(), "서울 성동구", "새벽러너");

        // then
        assertThat(response.author()).isNotNull();
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.author().nickname()).isEqualTo("새벽러너");
    }

    @Test
    @DisplayName("user-service 호출 실패 → 닉네임 null 전달 시 null 반영")
    void userService_실패_nickname_null_게시글생성() {
        // when
        CreatePostResponse response = postService.createPost(userId, defaultRequest(), "서울 성동구", null);

        // then
        assertThat(response.postId()).isNotNull();
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.author().nickname()).isNull();
    }
}