package com.sparta.ditto.chat.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.infrastructure.client.UserProfileClientResponse.UserProfileData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserServiceChatProfileAdapterTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private UserServiceClient userServiceClient;
    private UserServiceChatProfileAdapter adapter;

    @BeforeEach
    void setUp() {
        userServiceClient = mock(UserServiceClient.class);
        adapter = new UserServiceChatProfileAdapter(userServiceClient);
    }

    @Test
    @DisplayName("성공 - user-service 응답에서 닉네임·프로필 이미지를 매핑한다")
    void findProfile_success() {
        given(userServiceClient.getUserProfile(USER_ID))
                .willReturn(new UserProfileClientResponse(
                        new UserProfileData("홍길동", "https://img/p.png")));

        ChatSenderProfile profile = adapter.findProfile(USER_ID);

        assertThat(profile.nickname()).isEqualTo("홍길동");
        assertThat(profile.profileImageUrl()).isEqualTo("https://img/p.png");
    }

    @Test
    @DisplayName("폴백 - data가 null이면 unknown 프로필을 반환한다")
    void findProfile_nullData_returnsUnknown() {
        given(userServiceClient.getUserProfile(USER_ID))
                .willReturn(new UserProfileClientResponse(null));

        ChatSenderProfile profile = adapter.findProfile(USER_ID);

        assertThat(profile.nickname()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("폴백 - 조회 중 예외가 나도 발행을 막지 않고 unknown을 반환한다")
    void findProfile_exception_returnsUnknown() {
        when(userServiceClient.getUserProfile(USER_ID))
                .thenThrow(new RuntimeException("user-service down"));

        assertThatCode(() -> {
            ChatSenderProfile profile = adapter.findProfile(USER_ID);
            assertThat(profile.nickname()).isNull();
            assertThat(profile.profileImageUrl()).isNull();
        }).doesNotThrowAnyException();
    }
}