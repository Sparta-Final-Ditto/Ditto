package com.sparta.ditto.feed.domain.port;

public interface S3Port {
    String generatePresignedPutUrl(String s3Key, String contentType);
    boolean doesObjectExist(String s3Key);
}
