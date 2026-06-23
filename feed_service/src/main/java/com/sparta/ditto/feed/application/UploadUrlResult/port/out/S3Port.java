package com.sparta.ditto.feed.application.UploadUrlResult.port.out;

/**
 * S3 파일 업로드·존재 확인 포트 인터페이스.
 * generatePresignedPutUrl()은 클라이언트가 서버를 거치지 않고 S3에 직접 업로드할 수 있는 PUT URL을 발급한다.
 * doesObjectExist()는 게시글 생성 시 클라이언트가 실제로 업로드를 완료했는지 검증하는 데 사용한다.
 * 구현체(S3Adapter)는 AWS SDK v2를 사용한다.
 */
public interface S3Port {

    String generatePresignedPutUrl(String s3Key, String contentType);

    boolean doesObjectExist(String s3Key);
}
