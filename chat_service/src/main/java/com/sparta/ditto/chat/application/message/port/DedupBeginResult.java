package com.sparta.ditto.chat.application.message.port;

public record DedupBeginResult(Status status, String messageId) {

    public enum Status {
        NEW,
        DUPLICATE_COMPLETED,
        DUPLICATE_PROCESSING
    }

    public static DedupBeginResult newRequest() {
        return new DedupBeginResult(Status.NEW, null);
    }

    public static DedupBeginResult duplicateCompleted(String messageId) {
        return new DedupBeginResult(Status.DUPLICATE_COMPLETED, messageId);
    }

    public static DedupBeginResult duplicateProcessing() {
        return new DedupBeginResult(Status.DUPLICATE_PROCESSING, null);
    }
}
