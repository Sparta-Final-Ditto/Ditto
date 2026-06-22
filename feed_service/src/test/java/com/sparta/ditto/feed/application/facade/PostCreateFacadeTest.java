package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.port.NeighborhoodPort;
import com.sparta.ditto.feed.domain.port.S3Port;
import com.sparta.ditto.feed.domain.port.UserPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private UserPort userPort;

    @InjectMocks
    private PostCreateFacade postCreateFacade;

    private final UUID userId = UUID.randomUUID();

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
    @DisplayName("S3 객체 없음 → 400, S3_OBJECT_NOT_FOUND 업로드된 파일을 찾을 수 없습니다.")
    void S3객체_없음_S3_OBJECT_NOT_FOUND() {
        // given
        when(s3Port.doesObjectExist(anyString())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> postCreateFacade.createPost(userId, defaultRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("S3_OBJECT_NOT_FOUND");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("업로드된 파일을 찾을 수 없습니다.");
                });
    }

    @Test
    @DisplayName("S3 객체 존재 및 외부 API 성공 → 정상 호출 위임")
    void S3객체_존재_외부API_성공_정상호출() {
        // given
        CreatePostRequest request = defaultRequest();
        when(s3Port.doesObjectExist("feeds/test-uuid.mp4")).thenReturn(true);
        when(neighborhoodPort.resolveNeighborhood(37.5563, 127.0374)).thenReturn("서울 성동구");
        when(userPort.getNickname(userId)).thenReturn("새벽러너");

        // when
        postCreateFacade.createPost(userId, request);

        // then
        verify(s3Port).doesObjectExist("feeds/test-uuid.mp4");
        verify(neighborhoodPort).resolveNeighborhood(37.5563, 127.0374);
        verify(userPort).getNickname(userId);
        verify(postService).createPost(userId, request, "서울 성동구", "새벽러너");
    }

    @Test
    @DisplayName("Kakao API 실패 (neighborhoodPort null 반환) → null 전달하여 PostService 호출")
    void neighborhoodPort_null_반환_null_전달_PostService_호출() {
        // given
        CreatePostRequest request = defaultRequest();
        when(s3Port.doesObjectExist(anyString())).thenReturn(true);
        when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn(null);
        when(userPort.getNickname(any(UUID.class))).thenReturn("새벽러너");

        // when
        postCreateFacade.createPost(userId, request);

        // then
        verify(postService).createPost(userId, request, null, "새벽러너");
    }

    @Test
    @DisplayName("user-service 실패 (nickname null 반환) → null 전달하여 PostService 호출")
    void user_service_실패_nickname_null_전달_PostService_호출() {
        // given
        CreatePostRequest request = defaultRequest();
        when(s3Port.doesObjectExist(anyString())).thenReturn(true);
        when(neighborhoodPort.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn("서울 성동구");
        when(userPort.getNickname(any(UUID.class))).thenReturn(null);

        // when
        postCreateFacade.createPost(userId, request);

        // then
        verify(postService).createPost(userId, request, "서울 성동구", null);
    }
}
