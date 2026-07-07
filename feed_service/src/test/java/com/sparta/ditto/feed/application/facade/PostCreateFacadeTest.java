package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.application.port.NeighborhoodPort;
import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.domain.exception.S3ObjectNotFoundException;
import com.sparta.ditto.feed.domain.exception.S3ValidationFailedException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCreateFacadeTest {

    @Mock
    private PostService postService;

    @Mock
    private S3Port s3Port;

    @Mock
    private NeighborhoodPort neighborhoodPort;

    private PostCreateFacade postCreateFacade;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // 프로덕션의 supplyAsync(supplier, executor)를 동일 스레드에서 실행해 검증한다.
        // supplier가 던진 예외는 실제 executor와 동일하게 CompletableFuture에 담겨 join 시 CompletionException으로 노출된다.
        Executor directExecutor = Runnable::run;
        postCreateFacade = new PostCreateFacade(postService, s3Port, neighborhoodPort, directExecutor);
    }

    private CreatePostCommand defaultCommand() {
        return new CreatePostCommand(
                userId,
                "새벽러너",
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563,
                127.0374,
                "PUBLIC",
                true,
                List.of(new MediaFileItem("feeds/test-uuid.mp4", "VIDEO", 1))
        );
    }

    private CreatePostCommand commandWithMedia(List<MediaFileItem> media) {
        return new CreatePostCommand(
                userId, "새벽러너", "내용", List.of("#러닝"),
                37.5563, 127.0374, "PUBLIC", true, media);
    }

    @Test
    @DisplayName("전 파일 존재 및 외부 API 성공 → 정상 호출 위임")
    void S3객체_존재_외부API_성공_정상호출() {
        // given
        CreatePostCommand command = defaultCommand();
        when(s3Port.doesObjectExist("feeds/test-uuid.mp4")).thenReturn(true);
        when(neighborhoodPort.resolveNeighborhood(37.5563, 127.0374)).thenReturn("서울 성동구");

        // when
        postCreateFacade.createPost(command);

        // then
        verify(s3Port).doesObjectExist("feeds/test-uuid.mp4");
        verify(neighborhoodPort).resolveNeighborhood(37.5563, 127.0374);
        verify(postService).createPost(command, "서울 성동구", "새벽러너");
    }

    @Test
    @DisplayName("S3 객체 없음(port가 false) → 400, S3_OBJECT_NOT_FOUND")
    void S3객체_없음_S3_OBJECT_NOT_FOUND() {
        // given
        when(s3Port.doesObjectExist(anyString())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(defaultCommand()))
                .isInstanceOf(S3ObjectNotFoundException.class)
                .satisfies(e -> {
                    S3ObjectNotFoundException be = (S3ObjectNotFoundException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("S3_OBJECT_NOT_FOUND");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("업로드된 파일을 찾을 수 없습니다.");
                });
    }

    @Test
    @DisplayName("Kakao API 실패 (neighborhoodPort null 반환) → null 전달하여 PostService 호출")
    void neighborhoodPort_null_반환_null_전달_PostService_호출() {
        // given
        CreatePostCommand command = defaultCommand();
        when(s3Port.doesObjectExist(anyString())).thenReturn(true);
        when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn(null);

        // when
        postCreateFacade.createPost(command);

        // then
        verify(postService).createPost(command, null, "새벽러너");
    }

    @Test
    @DisplayName("S3 확인 불가(SdkClientException) → CompletionException이 아닌 S3ValidationFailedException(503)")
    void S3확인불가_SdkClientException_S3ValidationFailedException() {
        // given
        when(s3Port.doesObjectExist(anyString()))
                .thenThrow(SdkClientException.builder().message("네트워크 단절").build());

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(defaultCommand()))
                .isInstanceOf(S3ValidationFailedException.class)
                .satisfies(e -> assertThat(((S3ValidationFailedException) e).getErrorCode().getStatus())
                        .isEqualTo(503));
    }

    @Test
    @DisplayName("S3 권한 오류(S3Exception 403) → S3ValidationFailedException(503)")
    void S3권한오류_S3Exception_S3ValidationFailedException() {
        // given
        when(s3Port.doesObjectExist(anyString()))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(403).message("AccessDenied").build());

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(defaultCommand()))
                .isInstanceOf(S3ValidationFailedException.class);
    }

    @Test
    @DisplayName("포트가 도메인 예외(S3ValidationFailedException)를 던지면 그대로 전파")
    void 포트가_도메인예외_던지면_그대로_전파() {
        // given
        when(s3Port.doesObjectExist(anyString()))
                .thenThrow(new S3ValidationFailedException());

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(defaultCommand()))
                .isInstanceOf(S3ValidationFailedException.class);
    }

    @Test
    @DisplayName("여러 파일 중 1개만 확인 불가여도 S3ValidationFailedException으로 수렴")
    void 여러파일중_1개_확인불가_S3ValidationFailedException() {
        // given
        CreatePostCommand command = commandWithMedia(List.of(
                new MediaFileItem("feeds/a.png", "IMAGE", 1),
                new MediaFileItem("feeds/b.png", "IMAGE", 2)));
        when(s3Port.doesObjectExist("feeds/a.png")).thenReturn(true);
        when(s3Port.doesObjectExist("feeds/b.png"))
                .thenThrow(SdkClientException.builder().message("timeout").build());

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(command))
                .isInstanceOf(S3ValidationFailedException.class);
    }
}
