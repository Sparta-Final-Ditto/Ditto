package com.sparta.ditto.chat.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileClientResponse(UserProfileData data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserProfileData(String nickname, String profileImageUrl) {
    }
}
