package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.domain.exception.FeedErrorCode;
import com.sparta.ditto.feed.domain.port.S3Port;
import com.sparta.ditto.feed.presentation.dto.request.UploadUrlRequest.FileRequest;
import com.sparta.ditto.feed.presentation.dto.response.UploadUrlResponse;
import com.sparta.ditto.feed.presentation.dto.response.UploadUrlResponse.FileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadUrlService {

    // 파일 크기 제한 (bytes)
    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;   // 10MB
    private static final long VIDEO_MAX_SIZE = 170L * 1024 * 1024;  // 170MB

    // 파일 개수 제한
    private static final int IMAGE_MAX_COUNT = 5;
    private static final int VIDEO_MAX_COUNT = 1;
    private static final int TOTAL_MAX_COUNT = 6;

    // 허용 MIME 타입
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4");

    // S3 key 생성 시 파일 확장자 결정에 사용
    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "video/mp4", "mp4"
    );

    private final S3Port s3Port;

    /**
     * 클라이언트가 S3에 직접 업로드할 수 있는 presigned URL을 발급한다.
     * 클라이언트는 이 URL로 PUT 요청을 보내 파일을 S3에 업로드한 뒤,
     * 반환된 s3Key를 POST /posts 요청에 포함한다.
     */
    public UploadUrlResponse generateUploadUrls(List<FileRequest> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(FeedErrorCode.FILES_EMPTY);
        }

        // 허용되지 않은 MIME 타입 검사 (개수 검사보다 먼저 수행해 즉시 실패)
        for (FileRequest file : files) {
            if (!IMAGE_TYPES.contains(file.fileType()) && !VIDEO_TYPES.contains(file.fileType())) {
                throw new BusinessException(FeedErrorCode.INVALID_MEDIA_TYPE);
            }
        }

        long imageCount = files.stream().filter(f -> IMAGE_TYPES.contains(f.fileType())).count();
        long videoCount = files.stream().filter(f -> VIDEO_TYPES.contains(f.fileType())).count();

        // 합산 개수를 먼저 검사해 개별 이미지/영상 초과보다 우선 실패
        if (files.size() > TOTAL_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.MEDIA_COUNT_EXCEEDED);
        }
        if (imageCount > IMAGE_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.IMAGE_COUNT_EXCEEDED);
        }
        if (videoCount > VIDEO_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.VIDEO_COUNT_EXCEEDED);
        }

        // 파일 크기 검사
        for (FileRequest file : files) {
            if (IMAGE_TYPES.contains(file.fileType()) && file.fileSize() > IMAGE_MAX_SIZE) {
                throw new BusinessException(FeedErrorCode.IMAGE_SIZE_EXCEEDED);
            }
            if (VIDEO_TYPES.contains(file.fileType()) && file.fileSize() > VIDEO_MAX_SIZE) {
                throw new BusinessException(FeedErrorCode.VIDEO_SIZE_EXCEEDED);
            }
        }

        // s3Key 형식: feeds/{UUID}.{ext} — DB에는 이 키만 저장하고 CloudFront URL은 저장하지 않음
        List<FileResponse> fileResponses = files.stream()
                .map(file -> {
                    String ext = MIME_TO_EXT.get(file.fileType());
                    String s3Key = "feeds/" + UUID.randomUUID() + "." + ext;
                    String presignedUrl = s3Port.generatePresignedPutUrl(s3Key, file.fileType());
                    return new FileResponse(presignedUrl, s3Key);
                })
                .toList();

        return new UploadUrlResponse(fileResponses);
    }
}