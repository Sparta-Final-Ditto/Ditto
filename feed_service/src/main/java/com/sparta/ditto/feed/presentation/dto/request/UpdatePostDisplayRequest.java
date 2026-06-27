package com.sparta.ditto.feed.presentation.dto.request;

public record UpdatePostDisplayRequest(
        Boolean showLocation,
        String visibility
) {}