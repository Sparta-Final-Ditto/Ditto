package com.sparta.ditto.feed.infrastructure.s3;

import com.sparta.ditto.feed.application.port.S3Port;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

@Component
@RequiredArgsConstructor
/**
 * S3Port 구현체 — AWS SDK v2를 통한 S3 파일 업로드·존재 확인.
 * generatePresignedPutUrl()은 유효시간 10분의 Presigned PUT URL을 발급한다.
 * doesObjectExist()는 HeadObject 요청으로 파일 존재 여부를 확인하며,
 * NoSuchKeyException 발생 시 false를 반환한다.
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
