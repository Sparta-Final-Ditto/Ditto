package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.UploadUrlResult.port.out.NeighborhoodPort;
import com.sparta.ditto.feed.application.UploadUrlResult.port.out.S3Port;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.S3ObjectNotFoundException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시글 생성 유스케이스를 담당하는 퍼사드(Facade) 클래스.
 * <p>
 * 비즈니스 핵심 로직과 DB 트랜잭션 처리를 전담하는 {@link PostService}에 작업을 위임하기 전,
 * 외부 API 연동(Kakao Local API를 통한 주소 변환) 및 
 * 외부 인프라 I/O 작업(S3 업로드 파일 존재 여부 일괄 검증)을 트랜잭션 범위 밖에서 먼저 수행합니다.
 * 이를 통해 불필요한 DB 커넥션 점유 시간을 최소화하고 데드락이나 타임아웃 위험을 방지합니다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PostCreateFacade {

    private final PostService postService;
    private final S3Port s3Port;
    private final NeighborhoodPort neighborhoodPort;

    /**
     * 외부 API 및 I/O 작업(S3 객체 검증, 주소 변환)을 트랜잭션 외부에서 격리하여 실행한 후,
     * 모아진 데이터를 바탕으로 DB 트랜잭션을 전담하는 PostService에 위임한다.
     */
    public PostResult createPost(CreatePostCommand command) {
        List<MediaFileItem> mediaFiles =
                command.mediaFiles() != null ? command.mediaFiles() : List.of();

        validateS3Objects(mediaFiles);

        String neighborhood =
                neighborhoodPort.resolveNeighborhood(command.latitude(), command.longitude());

        return postService.createPost(command, neighborhood, command.nickname());
    }

    /**
     * S3 객체의 실제 존재 여부를 다중 파일 대상인 경우 비동기 병렬(CompletableFuture)로 일괄 검증한다.
     */
    private void validateS3Objects(List<MediaFileItem> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }

        List<CompletableFuture<Boolean>> futures = mediaFiles.stream()
                .map(m -> CompletableFuture.supplyAsync(
                        () -> s3Port.doesObjectExist(m.s3Key())))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean anyMissing = futures.stream()
                .map(CompletableFuture::join)
                .anyMatch(exists -> !exists);

        if (anyMissing) {
            throw new S3ObjectNotFoundException();
        }
    }
}
