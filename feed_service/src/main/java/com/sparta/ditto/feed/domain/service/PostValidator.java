package com.sparta.ditto.feed.domain.service;

import com.sparta.ditto.feed.domain.exception.EmptyPostException;

/**
 * 게시글 생성 도메인 규칙 검증.
 * DTO 어노테이션(@Valid)으로 처리할 수 없는 도메인 수준의 규칙을 검증한다.
 * content와 mediaFiles 두 값을 모두 알아야 판단 가능한 규칙이므로
 * 엔티티가 아닌 도메인 서비스로 분리했다.
 */
public class PostValidator {

    // 게시글에 이미지, 영상, 텍스트 중 최소 하나가 반드시 포함되어야 한다
    public static void validateContentOrMedia(String content, boolean hasMedia) {
        boolean contentBlank = content == null || content.isBlank();
        if (contentBlank && !hasMedia) {
            throw new EmptyPostException();
        }
    }
}
