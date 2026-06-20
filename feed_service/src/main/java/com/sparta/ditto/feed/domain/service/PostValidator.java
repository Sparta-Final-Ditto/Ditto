package com.sparta.ditto.feed.domain.service;

import com.sparta.ditto.feed.domain.exception.EmptyPostException;

/** 게시글 생성 도메인 규칙 검증 */
public class PostValidator {

    // 게시글에 이미지, 영상, 텍스트 중 최소 하나가 반드시 포함되어야 한다
    public static void validateContentOrMedia(String content, boolean hasMedia) {
        boolean contentBlank = content == null || content.isBlank();
        if (contentBlank && !hasMedia) {
            throw new EmptyPostException();
        }
    }
}
