package com.sparta.ditto.feed.application.port;

public interface OutboxEventPublisher {
    void publish(String topic, String payload);
}
