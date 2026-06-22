package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.UploadUrlCommand;
import com.sparta.ditto.feed.application.dto.UploadUrlCommand.FileItem;
import com.sparta.ditto.feed.application.dto.UploadUrlResult;
import com.sparta.ditto.feed.application.dto.UploadUrlResult.FileResult;
import com.sparta.ditto.feed.domain.exception.FilesEmptyException;
import com.sparta.ditto.feed.domain.exception.ImageCountExceededException;
import com.sparta.ditto.feed.domain.exception.ImageSizeExceededException;
import com.sparta.ditto.feed.domain.exception.InvalidMediaTypeException;
import com.sparta.ditto.feed.domain.exception.MediaCountExceededException;
import com.sparta.ditto.feed.domain.exception.VideoCountExceededException;
import com.sparta.ditto.feed.domain.exception.VideoSizeExceededException;
import com.sparta.ditto.feed.application.port.S3Port;
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

    public UploadUrlResult generateUploadUrls(UploadUrlCommand command) {
        List<FileItem> files = command.files();
        if (files == null || files.isEmpty()) {
            throw new FilesEmptyException();
        }

        for (FileItem file : files) {
            if (!IMAGE_TYPES.contains(file.fileType()) && !VIDEO_TYPES.contains(file.fileType())) {
                throw new InvalidMediaTypeException();
            }
        }

        long imageCount = files.stream().filter(f -> IMAGE_TYPES.contains(f.fileType())).count();
        long videoCount = files.stream().filter(f -> VIDEO_TYPES.contains(f.fileType())).count();

        if (files.size() > TOTAL_MAX_COUNT) {
            throw new MediaCountExceededException();
        }
        if (imageCount > IMAGE_MAX_COUNT) {
            throw new ImageCountExceededException();
        }
        if (videoCount > VIDEO_MAX_COUNT) {
            throw new VideoCountExceededException();
        }

        for (FileItem file : files) {
            if (IMAGE_TYPES.contains(file.fileType()) && file.fileSize() > IMAGE_MAX_SIZE) {
                throw new ImageSizeExceededException();
            }
            if (VIDEO_TYPES.contains(file.fileType()) && file.fileSize() > VIDEO_MAX_SIZE) {
                throw new VideoSizeExceededException();
            }
        }

        List<FileResult> fileResults = files.stream()
                .map(file -> {
                    String ext = MIME_TO_EXT.get(file.fileType());
                    String s3Key = "feeds/" + UUID.randomUUID() + "." + ext;
                    String presignedUrl = s3Port.generatePresignedPutUrl(s3Key, file.fileType());
                    return new FileResult(presignedUrl, s3Key);
                })
                .toList();

        return new UploadUrlResult(fileResults);
    }
}
