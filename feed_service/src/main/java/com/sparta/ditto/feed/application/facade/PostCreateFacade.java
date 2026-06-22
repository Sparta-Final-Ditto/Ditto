package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.PostResult;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.S3ObjectNotFoundException;
import com.sparta.ditto.feed.application.port.NeighborhoodPort;
import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.application.port.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public PostResult createPost(CreatePostCommand command) {
        List<MediaFileItem> mediaFiles = command.mediaFiles() != null ? command.mediaFiles() : List.of();

        validateS3Objects(mediaFiles);

        String neighborhood = neighborhoodPort.resolveNeighborhood(command.latitude(), command.longitude());

        String nickname = userPort.getNickname(command.userId());

        return postService.createPost(command, neighborhood, nickname);
    }

    /**
     * S3 객체의 실제 존재 여부를 다중 파일 대상인 경우 비동기 병렬(CompletableFuture)로 일괄 검증한다.
     */
    private void validateS3Objects(List<MediaFileItem> mediaFiles) {
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
