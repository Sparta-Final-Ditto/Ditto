package com.sparta.ditto.feed.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.feed.domain.exception.S3ValidationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link S3Adapter#doesObjectExist(String)} 단위 테스트.
 * "파일 없음"(false)과 "확인 불가"({@link S3ValidationFailedException})를 구분하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class S3AdapterTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private S3Adapter s3Adapter;

    @BeforeEach
    void setUp() {
        s3Adapter = new S3Adapter(s3Presigner, s3Client);
        ReflectionTestUtils.setField(s3Adapter, "bucket", "test-bucket");
    }

    @Test
    @DisplayName("객체 존재 → true")
    void objectExists_returnsTrue() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().build());

        assertThat(s3Adapter.doesObjectExist("feeds/exists.png")).isTrue();
    }

    @Test
    @DisplayName("NoSuchKeyException(파일 없음) → false")
    void noSuchKey_returnsFalse() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().build());

        assertThat(s3Adapter.doesObjectExist("feeds/missing.png")).isFalse();
    }

    @Test
    @DisplayName("S3Exception 403(권한 오류) → 확인 불가, S3ValidationFailedException")
    void accessDenied403_throwsValidationFailed() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow((S3Exception) S3Exception.builder().statusCode(403).message("AccessDenied").build());

        assertThatThrownBy(() -> s3Adapter.doesObjectExist("feeds/denied.png"))
                .isInstanceOf(S3ValidationFailedException.class)
                .satisfies(e -> assertThat(((S3ValidationFailedException) e).getErrorCode().getStatus())
                        .isEqualTo(503));
    }

    @Test
    @DisplayName("SdkClientException(네트워크/타임아웃) → 확인 불가, S3ValidationFailedException")
    void networkError_throwsValidationFailed() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(SdkClientException.builder().message("connection reset").build());

        assertThatThrownBy(() -> s3Adapter.doesObjectExist("feeds/timeout.png"))
                .isInstanceOf(S3ValidationFailedException.class);
    }
}
