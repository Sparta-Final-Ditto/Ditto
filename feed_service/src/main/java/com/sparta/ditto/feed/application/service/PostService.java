package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.service.PostValidator;
import com.sparta.ditto.feed.domain.type.LocationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
/** 게시글 생성 트랜잭션 처리 서비스 */
public class PostService {

    private final PostRepository postRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPort outboxEventPort;

    // application.yml 등 환경 변수로부터 주입받은 CloudFront CDN 도메인 주소
    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    /**
     * 게시글을 생성하고 생성된 게시글 정보를 반환한다.
     * 외부 API 호출 격리를 위해 트랜잭션 외부(PostCreateFacade)에서 수집 완료된
     * neighborhood, nickname 값을 인자로 받으며, 메소드 단위에서 선언적 트랜잭션을 시작한다.
     */
    @Transactional
    public CreatePostResponse createPost(UUID userId, CreatePostRequest request, String neighborhood, String nickname) {
        // 미디어 파일 요청 목록이 null인 경우 NullPointerException 방지를 위해 빈 리스트로 초기화
        List<MediaFileRequest> mediaFiles = request.mediaFiles() != null ? request.mediaFiles() : List.of();

        // 1. 요청에 포함된 공개범위 문자열을 검증하고 ENUM 타입으로 변환 (기본값 PUBLIC)
        LocationScope locationScope = LocationScope.from(request.locationScope());
        
        // 2. 비즈니스 규칙 검증: 텍스트(content)와 미디어 파일 중 최소 하나는 채워져 있는지 검사
        PostValidator.validateContentOrMedia(request.content(), !mediaFiles.isEmpty());

        // 3. 위치 정보 노출 여부 세팅 (기본값 true)
        boolean showLocation = request.showLocation() != null ? request.showLocation() : true;
        
        // 4. DB의 UNIQUE(post_id, tag) 제약조건 위반을 방어하기 위해 입력된 태그의 중복을 제거
        List<String> distinctTags = request.tags().stream().distinct().toList();

        // 5. 비정규화된 닉네임과 주소 정보를 포함하여 Post 엔티티 인스턴스 빌드
        Post post = new Post(userId, nickname, request.content(), neighborhood,
                request.latitude(), request.longitude(), locationScope, showLocation);

        // 5-1. 태그 엔티티들을 생성하여 Post의 연관관계 컬렉션에 추가
        distinctTags.forEach(tag -> post.getTags().add(new PostTag(post, tag)));
        
        // 5-2. 미디어 파일 엔티티들을 생성하여 Post 연관관계 컬렉션에 추가 (addMedia 내부에서 이미지/영상 개수 제한 검증)
        mediaFiles.forEach(m -> post.addMedia(new PostMedia(post, m.s3Key(), m.mediaType(), m.sortOrder())));

        // 5-3. 영속성 전이(CascadeType.ALL)를 통해 Post와 연관 엔티티(미디어, 태그)들을 DB에 일괄 저장
        Post savedPost = postRepository.save(post);

        // 5-4. Transactional Outbox 패턴: 비즈니스 원자성을 보장하기 위해 동일 트랜잭션 내에 Outbox 이벤트 적재
        outboxEventRepository.save(outboxEventPort.buildPostCreated(savedPost, userId, distinctTags));

        // 6. 데이터 저장이 완료된 영속성 객체를 포맷팅하여 최종 응답 DTO로 조립하여 반환
        return CreatePostResponse.from(savedPost, nickname, cloudfrontDomain);
    }
}