package com.sparta.ditto.feed.application.dto;

import java.util.List;

public record UploadUrlCommand(List<FileItem> files) {
    public record FileItem(String fileName, String fileType, Long fileSize) {}
}
