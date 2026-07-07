package com.sparta.ditto.feed.infrastructure.s3;

import com.sparta.ditto.feed.application.port.S3Port;
import com.sparta.ditto.feed.domain.exception.S3ValidationFailedException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * S3Port 구현체 — AWS SDK v2를 통한 S3 파일 업로드·존재 확인.
 * generatePresignedPutUrl()은 유효시간 10분의 Presigned PUT URL을 발급한다.
 * doesObjectExist()는 HeadObject 요청으로 파일 존재 여부를 확인한다.
 */
public class S3Adapter implements S3Port {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public String generatePresignedPutUrl(String s3Key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * S3 HeadObject로 파일 존재 여부를 확인한다.
     *
     * <ul>
     *   <li>존재 → {@code true}</li>
     *   <li>파일 없음({@link NoSuchKeyException}) → {@code false}</li>
     *   <li>존재 여부를 <b>확인할 수 없음</b>(권한 오류 S3Exception(예: 403 AccessDenied),
     *       네트워크·타임아웃 SdkClientException 등) → {@link S3ValidationFailedException}.
     *       "확인 불가"를 "파일 없음"({@code false})으로 뭉개지 않는다.</li>
     * </ul>
     *
     * <p><b>IAM 엣지케이스:</b> HeadObject는 {@code s3:ListBucket} 권한이 없으면 존재하지 않는 키에 대해
     * 404가 아니라 403(AccessDenied)을 반환한다. 따라서 403을 "파일 없음"으로 해석하지 않고
     * "확인 불가"({@link S3ValidationFailedException})로 처리한다.</p>
     */
    @Override
    public boolean doesObjectExist(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            // S3Exception(403 등)·SdkClientException(네트워크/타임아웃)을 포함한 SDK 오류 전반.
            // NoSuchKeyException은 위에서 이미 처리되므로 여기 도달하지 않는다. (재시도는 SDK 내부에 위임)
            log.warn("S3 객체 존재 확인 실패(확인 불가) key={}", s3Key, e);
            throw new S3ValidationFailedException();
        }
    }

    @Override
    public void deleteObjects(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> identifiers = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .collect(Collectors.toList());

        DeleteObjectsResponse response = s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(identifiers).build())
                        .build()
        );

        if (!response.errors().isEmpty()) {
            throw new RuntimeException(
                    "S3 객체 삭제 중 일부 실패: " + response.errors());
        }
    }
}
