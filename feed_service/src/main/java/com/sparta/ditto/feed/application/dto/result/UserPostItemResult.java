package com.sparta.ditto.feed.application.dto.result;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import java.util.List;
import java.util.UUID;

public record UserPostItemResult(
        UUID postId,
        String thumbnailUrl,
        String mediaType,
        String contentSummary
) {

    /**
     * 게시글 → 3*3 그리드 아이템 결과로 변환한다.
     *
     * <p>대표 미디어 선택 기준: sort_order=1인 첫 번째 미디어 (mediaList는 @OrderBy("sortOrder ASC") 보장)</p>
     * <ul>
     *   <li>IMAGE: thumbnailUrl=CDN URL, mediaType=IMAGE, contentSummary=null</li>
     *   <li>VIDEO: thumbnailUrl=원본 CDN URL, mediaType=VIDEO, contentSummary=null</li>
     *   <li>미디어 없음: thumbnailUrl=null, mediaType=null, contentSummary=content 최대 50자</li>
     * </ul>
     */
    public static UserPostItemResult from(Post post, String cloudfrontDomain) {
        String domain = (cloudfrontDomain != null && cloudfrontDomain.endsWith("/"))
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;

        List<PostMedia> mediaList = post.getMediaList();
        if (!mediaList.isEmpty()) {
            PostMedia first = mediaList.get(0);
            return new UserPostItemResult(
                    post.getId(),
                    domain + "/" + first.getS3Key(),
                    first.getMediaType().name(),
                    null
            );
        }

        String content = post.getContent();
        String summary = (content != null && content.length() > 50)
                ? content.substring(0, 50)
                : content;
        return new UserPostItemResult(post.getId(), null, null, summary);
    }
}
