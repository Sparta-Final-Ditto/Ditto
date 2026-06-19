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

    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;
    private static final long VIDEO_MAX_SIZE = 170L * 1024 * 1024;
    private static final int IMAGE_MAX_COUNT = 5;
    private static final int VIDEO_MAX_COUNT = 1;
    private static final int TOTAL_MAX_COUNT = 6;

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4");

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "video/mp4", "mp4"
    );

    private final S3Port s3Port;

    public UploadUrlResponse generateUploadUrls(List<FileRequest> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(FeedErrorCode.FILES_EMPTY);
        }

        for (FileRequest file : files) {
            if (!IMAGE_TYPES.contains(file.fileType()) && !VIDEO_TYPES.contains(file.fileType())) {
                throw new BusinessException(FeedErrorCode.INVALID_MEDIA_TYPE);
            }
        }

        long imageCount = files.stream().filter(f -> IMAGE_TYPES.contains(f.fileType())).count();
        long videoCount = files.stream().filter(f -> VIDEO_TYPES.contains(f.fileType())).count();

        if (files.size() > TOTAL_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.MEDIA_COUNT_EXCEEDED);
        }
        if (imageCount > IMAGE_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.IMAGE_COUNT_EXCEEDED);
        }
        if (videoCount > VIDEO_MAX_COUNT) {
            throw new BusinessException(FeedErrorCode.VIDEO_COUNT_EXCEEDED);
        }

        for (FileRequest file : files) {
            if (IMAGE_TYPES.contains(file.fileType()) && file.fileSize() > IMAGE_MAX_SIZE) {
                throw new BusinessException(FeedErrorCode.IMAGE_SIZE_EXCEEDED);
            }
            if (VIDEO_TYPES.contains(file.fileType()) && file.fileSize() > VIDEO_MAX_SIZE) {
                throw new BusinessException(FeedErrorCode.VIDEO_SIZE_EXCEEDED);
            }
        }

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
