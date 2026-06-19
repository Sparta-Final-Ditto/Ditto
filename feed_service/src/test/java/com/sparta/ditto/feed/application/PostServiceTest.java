package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.port.NeighborhoodPort;
import com.sparta.ditto.feed.domain.port.S3Port;
import com.sparta.ditto.feed.domain.port.UserPort;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private S3Port s3Port;

    @Mock
    private NeighborhoodPort neighborhoodPort;

    @Mock
    private UserPort userPort;

    @InjectMocks
    private PostService postService;

    private final UUID userId = UUID.randomUUID();
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);

        lenient().when(s3Port.doesObjectExist(anyString())).thenReturn(true);
        lenient().when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn("서울 성동구");
        lenient().when(userPort.getNickname(any(UUID.class))).thenReturn("새벽러너");
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(post, "createdAt", Instant.now());
            return post;
        });
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
    void tc002_1_필수필드와_content_전달_게시글생성() {
        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.postId()).isNotNull();
        assertThat(response.content()).isEqualTo("오늘 새벽 러닝 완료!");
    }

    @Test
    @DisplayName("content=null, mediaFiles 1개 이상 → 201, 게시글 생성")
    void tc002_2_content_null_mediaFiles_존재_게시글생성() {
        CreatePostRequest request = new CreatePostRequest(
                null,
                List.of("#러닝"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test.mp4", "VIDEO", 1))
        );

        CreatePostResponse response = postService.createPost(userId, request);

        assertThat(response.postId()).isNotNull();
        assertThat(response.content()).isNull();
    }

    @Test
    @DisplayName("mediaFiles=[], content 존재 → 201, 게시글 생성")
    void tc002_3_mediaFiles_비어있고_content_존재_게시글생성() {
        CreatePostRequest request = new CreatePostRequest(
                "텍스트만 있는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of()
        );

        CreatePostResponse response = postService.createPost(userId, request);

        assertThat(response.postId()).isNotNull();
        assertThat(response.mediaFiles()).isEmpty();
    }

    @Test
    @DisplayName("모든 필드 전달 → 201, 모든 필드 반영")
    void tc002_4_모든필드_전달_모든필드_반영() {
        CreatePostRequest request = new CreatePostRequest(
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563, 127.0374,
                "FOLLOWERS_ONLY", false,
                List.of(new MediaFileRequest("feeds/test.mp4", "VIDEO", 1))
        );

        CreatePostResponse response = postService.createPost(userId, request);

        assertThat(response.content()).isEqualTo("오늘 새벽 러닝 완료!");
        assertThat(response.tags()).containsExactlyInAnyOrder("#새벽운동", "#러닝");
        assertThat(response.showLocation()).isFalse();
        assertThat(response.neighborhood()).isEqualTo("서울 성동구");
        assertThat(response.likeCount()).isZero();
        assertThat(response.isLiked()).isFalse();
        assertThat(response.commentCount()).isZero();
    }

    @Test
    @DisplayName("locationScope 누락 → PUBLIC으로 저장")
    void tc002_5_locationScope_누락_PUBLIC_기본값() {
        CreatePostRequest request = new CreatePostRequest(
                "공개 범위 없는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                null, true,
                List.of()
        );

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        postService.createPost(userId, request);

        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getLocationScope().name()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("showLocation 누락 → true로 저장")
    void tc002_6_showLocation_누락_true_기본값() {
        CreatePostRequest request = new CreatePostRequest(
                "showLocation 없는 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", null,
                List.of()
        );

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        postService.createPost(userId, request);

        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getShowLocation()).isTrue();
    }

    @Test
    @DisplayName("tags=[] → 400, VALIDATION_ERROR 태그는 최소 1개 이상 입력해주세요.")
    void tc002_7_tags_비어있음_TAG_MIN_REQUIRED() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of(),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("태그는 최소 1개 이상 입력해주세요.");
                });
    }

    @Test
    @DisplayName("태그 11개 → 400, VALIDATION_ERROR 태그는 최대 10개까지 입력할 수 있습니다.")
    void tc002_8_태그11개_TAG_MAX_EXCEEDED() {
        List<String> tags = List.of("#1", "#2", "#3", "#4", "#5", "#6", "#7", "#8", "#9", "#10", "#11");
        CreatePostRequest request = new CreatePostRequest(
                "내용", tags,
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("태그는 최대 10개까지 입력할 수 있습니다.");
                });
    }

    @Test
    @DisplayName("latitude 누락 → 400, VALIDATION_ERROR 위치 정보는 필수입니다.")
    void tc002_9_latitude_누락_LOCATION_REQUIRED() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                null, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("위치 정보는 필수입니다.");
                });
    }

    @Test
    @DisplayName("longitude 누락 → 400, VALIDATION_ERROR 위치 정보는 필수입니다.")
    void tc002_9_longitude_누락_LOCATION_REQUIRED() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, null,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("위치 정보는 필수입니다.");
                });
    }

    @Test
    @DisplayName("content 501자 → 400, VALIDATION_ERROR 게시글 본문은 500자 이내로 입력해주세요.")
    void tc002_10_content_501자_CONTENT_TOO_LONG() {
        CreatePostRequest request = new CreatePostRequest(
                "A".repeat(501), List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("게시글 본문은 500자 이내로 입력해주세요.");
                });
    }

    @Test
    @DisplayName("locationScope=INVALID → 400, VALIDATION_ERROR 공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.")
    void tc002_11_locationScope_유효하지않음_INVALID_LOCATION_SCOPE() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "INVALID", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.");
                });
    }

    @Test
    @DisplayName("mediaType=FILE → 400, VALIDATION_ERROR 미디어 타입은 IMAGE, VIDEO 중 선택해주세요.")
    void tc002_12_mediaType_FILE_INVALID_POST_MEDIA_TYPE() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test.pdf", "FILE", 1))
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("미디어 타입은 IMAGE, VIDEO 중 선택해주세요.");
                });
    }

    @Test
    @DisplayName("S3 객체 없음 → 400, S3_OBJECT_NOT_FOUND 업로드된 파일을 찾을 수 없습니다.")
    void tc002_13_S3객체_없음_S3_OBJECT_NOT_FOUND() {
        when(s3Port.doesObjectExist(anyString())).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(userId, defaultRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("S3_OBJECT_NOT_FOUND");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("업로드된 파일을 찾을 수 없습니다.");
                });
    }

    @Test
    @DisplayName("S3 객체 존재 → 201, 게시글 생성")
    void tc002_14_S3객체_존재_게시글생성() {
        when(s3Port.doesObjectExist("feeds/test-uuid.mp4")).thenReturn(true);

        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.postId()).isNotNull();
    }

    @Test
    @DisplayName("neighborhoodPort.resolveNeighborhood 호출 → 결과가 응답 neighborhood에 반영")
    void tc002_15_neighborhoodPort_호출_neighborhood_응답에_반영() {
        when(neighborhoodPort.resolveNeighborhood(37.5563, 127.0374)).thenReturn("서울 성동구");

        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        verify(neighborhoodPort).resolveNeighborhood(37.5563, 127.0374);
        assertThat(response.neighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("neighborhoodPort.resolveNeighborhood 호출 → 결과가 Post에 저장됨")
    void tc002_16_neighborhoodPort_호출_결과_Post에_저장() {
        when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn("서울 성동구");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        postService.createPost(userId, defaultRequest());

        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getNeighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("Kakao API 실패 (neighborhoodPort null 반환) → 201, neighborhood=null")
    void tc002_17_neighborhoodPort_null_반환_neighborhood_null_게시글생성() {
        when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn(null);

        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.postId()).isNotNull();
        assertThat(response.neighborhood()).isNull();
    }

    @Test
    @DisplayName("정상 생성 → 응답에 latitude, longitude 필드 없음")
    void tc002_18_응답에_latitude_longitude_필드_없음() {
        var componentNames = Arrays.stream(CreatePostResponse.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList();

        assertThat(componentNames).doesNotContain("latitude", "longitude");
    }

    @Test
    @DisplayName("미디어 포함 → mediaUrl이 CloudFront URL로 반환")
    void tc002_19_미디어포함_CloudFront_mediaUrl_반환() {
        CreatePostRequest request = new CreatePostRequest(
                "미디어 포함 게시글",
                List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(new MediaFileRequest("feeds/test-uuid.mp4", "VIDEO", 1))
        );

        CreatePostResponse response = postService.createPost(userId, request);

        assertThat(response.mediaFiles()).hasSize(1);
        assertThat(response.mediaFiles().get(0).s3Key()).isEqualTo("feeds/test-uuid.mp4");
        assertThat(response.mediaFiles().get(0).mediaUrl())
                .isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/test-uuid.mp4");
        assertThat(response.mediaFiles().get(0).mediaType()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("정상 생성 → 응답에 createdAt 포함")
    void tc002_20_정상생성_createdAt_포함() {
        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("정상 생성 → POST_CREATED OutboxEvent PENDING 상태로 저장")
    void tc002_21_정상생성_POST_CREATED_Outbox_저장() {
        postService.createPost(userId, defaultRequest());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("post-events");
        assertThat(captor.getValue().getEventType()).isEqualTo("POST_CREATED");
        assertThat(captor.getValue().getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("content 비어 있고 mediaFiles=[] → 400, VALIDATION_ERROR 이미지, 영상, 텍스트 중 하나는 반드시 입력해주세요.")
    void tc002_22_content_비어있고_mediaFiles_비어있음_EMPTY_POST() {
        CreatePostRequest request = new CreatePostRequest(
                "", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("이미지, 영상, 텍스트 중 하나는 반드시 입력해주세요.");
                });
    }

    @Test
    @DisplayName("mediaFiles 내 sortOrder 중복 → 400, VALIDATION_ERROR")
    void tc002_23_sortOrder_중복_예외() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileRequest("feeds/img1.jpg", "IMAGE", 1),
                        new MediaFileRequest("feeds/img2.jpg", "IMAGE", 1)
                )
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("tags 내 중복 태그 포함 → 201, 중복 제거 후 저장")
    void tc002_24_중복태그_중복제거_저장() {
        CreatePostRequest request = new CreatePostRequest(
                "내용",
                List.of("#러닝", "#러닝", "#새벽"),
                37.5563, 127.0374,
                "PUBLIC", true, List.of()
        );

        CreatePostResponse response = postService.createPost(userId, request);

        assertThat(response.tags()).containsExactlyInAnyOrder("#러닝", "#새벽");
        assertThat(response.tags()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이미지 6장 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void tc002_25_이미지6장_미디어개수초과_예외() {
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

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("영상 2개 → 400, VALIDATION_ERROR 미디어 개수 초과")
    void tc002_25_영상2개_미디어개수초과_예외() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 127.0374,
                "PUBLIC", true,
                List.of(
                        new MediaFileRequest("feeds/1.mp4", "VIDEO", 1),
                        new MediaFileRequest("feeds/2.mp4", "VIDEO", 2)
                )
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("latitude 유효 범위 초과 (91.0) → 400, VALIDATION_ERROR")
    void tc002_26_latitude_범위초과_예외() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                91.0, 127.0374,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("longitude 유효 범위 초과 (181.0) → 400, VALIDATION_ERROR")
    void tc002_26_longitude_범위초과_예외() {
        CreatePostRequest request = new CreatePostRequest(
                "내용", List.of("#태그"),
                37.5563, 181.0,
                "PUBLIC", true, List.of()
        );

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                });
    }

    // TC-002-27: X-User-Id 헤더 누락 → 401 은 Controller 계층 테스트 대상 (서비스 테스트 범위 제외)

    @Test
    @DisplayName("정상 생성 → 응답에 author.userId, author.nickname 포함")
    void tc002_28_정상생성_author_포함() {
        when(userPort.getNickname(userId)).thenReturn("새벽러너");

        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.author()).isNotNull();
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.author().nickname()).isEqualTo("새벽러너");
    }

    @Test
    @DisplayName("user-service 호출 실패 → 201, author.nickname=null")
    void tc002_29_userService_실패_nickname_null_게시글생성() {
        when(userPort.getNickname(any(UUID.class))).thenReturn(null);

        CreatePostResponse response = postService.createPost(userId, defaultRequest());

        assertThat(response.postId()).isNotNull();
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.author().nickname()).isNull();
    }
}