package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.exception.FeedErrorCode;
import com.sparta.ditto.feed.domain.port.NeighborhoodPort;
import com.sparta.ditto.feed.domain.port.S3Port;
import com.sparta.ditto.feed.domain.port.UserPort;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.domain.type.MediaType;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse.AuthorResponse;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse.MediaFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class PostService {

    private final TransactionTemplate transactionTemplate;
    private final PostRepository postRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final S3Port s3Port;
    private final NeighborhoodPort neighborhoodPort;
    private final UserPort userPort;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    /**
     * 게시글을 생성하고 생성된 게시글 정보를 반환한다.
     *
     * 처리 순서:
     * 1. 입력값 검증 (태그 → 위치 → 본문 → 공개범위 → 미디어타입 → 콘텐츠 존재 → sortOrder → 개수 → S3)
     * 2. 역지오코딩으로 neighborhood 조회 (Redis 캐시 우선, cache miss 시 Kakao API 호출)
     * 3. user-service에서 nickname 조회
     * 4. Post, PostTag, PostMedia, OutboxEvent를 단일 트랜잭션으로 저장
     */
    public CreatePostResponse createPost(UUID userId, CreatePostRequest request) {
        List<MediaFileRequest> mediaFiles = request.mediaFiles() != null ? request.mediaFiles() : List.of();

        // 검증은 S3/외부 API 호출보다 먼저 수행해 불필요한 I/O를 방지
        validateTags(request.tags());
        validateLocation(request.latitude(), request.longitude());
        validateContent(request.content());
        LocationScope locationScope = parseLocationScope(request.locationScope());
        // mediaType 파싱을 개수/S3 검증보다 먼저 수행해 잘못된 타입이면 즉시 실패
        List<MediaType> parsedMediaTypes = parseMediaTypes(mediaFiles);
        validateContentOrMedia(request.content(), mediaFiles);
        validateSortOrderUnique(mediaFiles);
        validateMediaCount(parsedMediaTypes);
        // S3 존재 확인은 외부 호출이므로 다른 검증을 모두 통과한 뒤 마지막에 수행
        validateS3Objects(mediaFiles);

        // Kakao API 장애 시 null을 반환하고 게시글 생성을 계속 진행
        String neighborhood = neighborhoodPort.resolveNeighborhood(request.latitude(), request.longitude());
        // user-service 장애 시 null을 반환하고 게시글 생성을 계속 진행
        String nickname = userPort.getNickname(userId);

        boolean showLocation = request.showLocation() != null ? request.showLocation() : true;
        // DB의 UNIQUE(post_id, tag) 제약 위반 방지를 위해 중복 태그를 미리 제거
        List<String> distinctTags = request.tags().stream().distinct().toList();

        // 외부 API 호출이 끝난 뒤 DB 저장만 트랜잭션으로 묶음
        Post savedPost = transactionTemplate.execute(status -> {
            Post post = new Post(userId, request.content(), neighborhood,
                    request.latitude(), request.longitude(), locationScope, showLocation);

            // PostTag, PostMedia는 Post와 cascade=ALL로 연관되어 있어 Post 저장 시 함께 저장됨
            distinctTags.forEach(tag -> post.getTags().add(new PostTag(post, tag)));
            for (int i = 0; i < mediaFiles.size(); i++) {
                MediaFileRequest m = mediaFiles.get(i);
                post.getMediaList().add(new PostMedia(post, m.s3Key(), parsedMediaTypes.get(i), m.sortOrder()));
            }

            Post saved = postRepository.save(post);

            // Transactional Outbox 패턴: Post 저장과 같은 트랜잭션에 이벤트를 기록해 발행 유실 방지
            outboxEventRepository.save(new OutboxEvent("post-events", "POST_CREATED",
                    buildOutboxPayload(saved, userId, distinctTags)));

            return saved;
        });

        return buildResponse(savedPost, userId, nickname);
    }

    // tags는 null/빈 배열 모두 허용하지 않음
    private void validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new BusinessException(FeedErrorCode.TAG_MIN_REQUIRED);
        }
        if (tags.size() > 10) {
            throw new BusinessException(FeedErrorCode.TAG_MAX_EXCEEDED);
        }
    }

    // latitude: -90~90, longitude: -180~180 (GPS 표준 범위)
    private void validateLocation(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(FeedErrorCode.LOCATION_REQUIRED);
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessException(FeedErrorCode.COORDINATE_OUT_OF_RANGE);
        }
    }

    // content는 null 허용 (미디어만 있는 게시글 가능), 입력 시 500자 제한
    private void validateContent(String content) {
        if (content != null && content.length() > 500) {
            throw new BusinessException(FeedErrorCode.CONTENT_TOO_LONG);
        }
    }

    // null이면 기본값 PUBLIC 적용, 알 수 없는 값이면 예외 (Bean Validation 대신 서비스에서 처리해 VALIDATION_ERROR 코드 반환)
    private LocationScope parseLocationScope(String locationScope) {
        if (locationScope == null) {
            return LocationScope.PUBLIC;
        }
        try {
            return LocationScope.valueOf(locationScope);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(FeedErrorCode.INVALID_LOCATION_SCOPE);
        }
    }

    // 파싱 결과를 리스트로 반환해 이후 PostMedia 생성 시 재사용 (valueOf 중복 호출 방지)
    private List<MediaType> parseMediaTypes(List<MediaFileRequest> mediaFiles) {
        return mediaFiles.stream().map(m -> {
            try {
                return MediaType.valueOf(m.mediaType());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(FeedErrorCode.INVALID_POST_MEDIA_TYPE);
            }
        }).toList();
    }

    // content와 mediaFiles 모두 없으면 저장할 콘텐츠가 없으므로 실패 처리
    private void validateContentOrMedia(String content, List<MediaFileRequest> mediaFiles) {
        boolean contentBlank = content == null || content.isBlank();
        if (contentBlank && mediaFiles.isEmpty()) {
            throw new BusinessException(FeedErrorCode.EMPTY_POST);
        }
    }

    // post_media 테이블의 UNIQUE(post_id, sort_order) 제약 위반 방지
    private void validateSortOrderUnique(List<MediaFileRequest> mediaFiles) {
        long distinct = mediaFiles.stream().map(MediaFileRequest::sortOrder).distinct().count();
        if (distinct != mediaFiles.size()) {
            throw new BusinessException(FeedErrorCode.DUPLICATE_SORT_ORDER);
        }
    }

    // 이미지 최대 5장, 영상 최대 1개 (업로드 URL 발급 시 검증과 동일한 기준)
    private void validateMediaCount(List<MediaType> parsedMediaTypes) {
        long imageCount = parsedMediaTypes.stream().filter(t -> t == MediaType.IMAGE).count();
        long videoCount = parsedMediaTypes.stream().filter(t -> t == MediaType.VIDEO).count();
        if (imageCount > 5) {
            throw new BusinessException(FeedErrorCode.IMAGE_COUNT_EXCEEDED);
        }
        if (videoCount > 1) {
            throw new BusinessException(FeedErrorCode.VIDEO_COUNT_EXCEEDED);
        }
    }

    // 클라이언트가 실제로 S3에 업로드했는지 확인 (presigned URL 발급 후 미업로드 방지)
    // 다중 파일을 병렬로 검증해 응답 지연 최소화
    private void validateS3Objects(List<MediaFileRequest> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }

        List<CompletableFuture<Boolean>> futures = mediaFiles.stream()
                .map(m -> CompletableFuture.supplyAsync(() -> s3Port.doesObjectExist(m.s3Key())))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean anyMissing = futures.stream()
                .map(CompletableFuture::join)
                .anyMatch(exists -> !exists);

        if (anyMissing) {
            throw new BusinessException(FeedErrorCode.S3_OBJECT_NOT_FOUND);
        }
    }

    // POST_CREATED payload 직렬화
    private String buildOutboxPayload(Post post, UUID userId, List<String> tags) {
        record Payload(String postId, String userId, String content, List<String> tags,
                       String neighborhood, Double latitude, Double longitude, String createdAt) {}
        try {
            return OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(), userId.toString(),
                    post.getContent(), tags, post.getNeighborhood(),
                    post.getLatitude(), post.getLongitude(),
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : null
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // mediaUrl: S3 원본 URL 대신 CloudFront URL로 변환해 응답 (보안 및 CDN 활용)
    private CreatePostResponse buildResponse(Post savedPost, UUID userId, String nickname) {
        List<MediaFileResponse> mediaFiles = savedPost.getMediaList().stream()
                .map(m -> new MediaFileResponse(
                        m.getS3Key(),
                        cloudfrontDomain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()
                ))
                .toList();

        List<String> tags = savedPost.getTags().stream()
                .map(PostTag::getTag)
                .toList();

        // likeCount, isLiked, commentCount는 생성 직후이므로 항상 0/false 고정
        return new CreatePostResponse(
                savedPost.getId(),
                new AuthorResponse(userId, nickname),
                savedPost.getContent(),
                savedPost.getNeighborhood(),
                tags,
                mediaFiles,
                0,
                false,
                0,
                savedPost.getShowLocation(),
                savedPost.getCreatedAt()
        );
    }
}