package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.S3ObjectNotFoundException;
import com.sparta.ditto.feed.application.port.NeighborhoodPort;
import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.application.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PostCreateFacade {

    private final PostService postService;
    private final S3Port s3Port;
    private final NeighborhoodPort neighborhoodPort;
    private final UserPort userPort;

    /**
     * 외부 API 및 I/O 작업(S3 객체 검증, 주소 변환, 유저 정보 조회)을 트랜잭션 외부에서 격리하여 실행한 후,
     * 모아진 데이터를 바탕으로 DB 트랜잭션을 전담하는 PostService에 위임한다.
     */
    public CreatePostResponse createPost(UUID userId, CreatePostRequest request) {
        List<MediaFileRequest> mediaFiles = request.mediaFiles() != null ? request.mediaFiles() : List.of();

        // 1. 클라이언트가 실제로 S3에 업로드 완료했는지 병렬 검증 (외부 I/O)
        validateS3Objects(mediaFiles);

        // 2. 위경도 좌표를 바탕으로 한글 동네명 변환 (외부 I/O - 카카오 Local API 호출)
        String neighborhood = neighborhoodPort.resolveNeighborhood(request.latitude(), request.longitude());

        // 3. user-service 통신을 통해 게시글 생성 시점의 작성자 닉네임 조회 (외부 I/O)
        String nickname = userPort.getNickname(userId);

        // 4. 수집된 정보를 넘겨 DB 트랜잭션 저장 로직 수행
        return postService.createPost(userId, request, neighborhood, nickname);
    }

    /**
     * S3 객체의 실제 존재 여부를 다중 파일 대상인 경우 비동기 병렬(CompletableFuture)로 일괄 검증한다.
     */
    private void validateS3Objects(List<MediaFileRequest> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }

        List<CompletableFuture<Boolean>> futures = mediaFiles.stream()
                .map(m -> CompletableFuture.supplyAsync(() -> s3Port.doesObjectExist(m.s3Key())))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean anyMissing = futures.stream().map(CompletableFuture::join).anyMatch(exists -> !exists);

        if (anyMissing) {
            throw new S3ObjectNotFoundException();
        }
    }
}
