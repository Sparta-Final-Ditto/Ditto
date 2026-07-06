package com.sparta.ditto.assistant.evaluation;

/** 골든셋 한 문항 청킹 */
public record GoldenSetItem(
        String id, String category, String question, String expectedDocumentId, String note) {

    private static final String NO_EXPECTED_DOCUMENT = "NONE";

    /** 정답 문서가 없는 hallucination trap 문항인지 여부 */
    public boolean isTrap() {
        return NO_EXPECTED_DOCUMENT.equals(expectedDocumentId);
    }
}
