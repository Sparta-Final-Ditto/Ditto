package com.sparta.ditto.feed.application.UploadUrlResult.port.out;

public interface OutboxEventPublisher {
    void publish(String topic, String payload);
}
