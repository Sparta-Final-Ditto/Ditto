package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.port.NeighborhoodPort;
import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.S3ObjectNotFoundException;
import com.sparta.ditto.feed.domain.exception.S3ValidationFailedException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class PostCreateFacade {

    private final PostService postService;
    private final S3Port s3Port;
    private final NeighborhoodPort neighborhoodPort;
    private final Executor s3ValidationExecutor;

    public PostCreateFacade(
            PostService postService,
            S3Port s3Port,
            NeighborhoodPort neighborhoodPort,
            // 빈 이름은 S3ValidationExecutorConfig.S3_VALIDATION_EXECUTOR와 일치해야 한다
            // (application → infrastructure import 금지 규칙 때문에 리터럴로 바인딩).
            @Qualifier("s3ValidationExecutor") Executor s3ValidationExecutor) {
        this.postService = postService;
        this.s3Port = s3Port;
        this.neighborhoodPort = neighborhoodPort;
        this.s3ValidationExecutor = s3ValidationExecutor;
    }

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
     * S3 객체의 실제 존재 여부를 다중 파일 대상인 경우 전용 executor에서 병렬(CompletableFuture)로 일괄 검증한다.
     *
     * <p>블로킹 I/O가 공용 {@code ForkJoinPool.commonPool}을 점유하지 않도록 S3 검증 전용 executor를 사용한다.
     * 비동기 실행 중 발생한 예외는 {@link CompletionException}으로 래핑되므로, cause를 풀어 도메인 예외로
     * 재던진다. 이를 통해 {@code CompletionException}이 컨트롤러까지 새어나가 500으로 노출되는 것을 막는다.</p>
     *
     * <ul>
     *   <li>일부 파일 없음(port가 false) → {@link S3ObjectNotFoundException}(400)</li>
     *   <li>존재 확인 불가(권한/네트워크 오류) → {@link S3ValidationFailedException}(503)</li>
     * </ul>
     */
    private void validateS3Objects(List<MediaFileItem> mediaFiles) {
        if (mediaFiles.isEmpty()) {
            return;
        }

        List<CompletableFuture<Boolean>> futures = mediaFiles.stream()
                .map(m -> CompletableFuture.supplyAsync(
                        () -> s3Port.doesObjectExist(m.s3Key()), s3ValidationExecutor))
                .toList();

        boolean anyMissing;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            anyMissing = futures.stream()
                    .map(CompletableFuture::join)
                    .anyMatch(exists -> !exists);
        } catch (CompletionException e) {
            throw unwrapValidationFailure(e);
        }

        if (anyMissing) {
            throw new S3ObjectNotFoundException();
        }
    }

    /**
     * 비동기 검증에서 터진 {@link CompletionException}의 cause를 도메인 예외로 변환한다.
     * cause가 이미 도메인 예외(예: S3Adapter가 던진 {@link S3ValidationFailedException})면 그대로 전파하고,
     * 알 수 없는 원인은 확인 불가로 간주(fail-closed)해 {@link S3ValidationFailedException}로 수렴한다.
     */
    private RuntimeException unwrapValidationFailure(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof BusinessException businessException) {
            return businessException;
        }
        return new S3ValidationFailedException();
    }
}
