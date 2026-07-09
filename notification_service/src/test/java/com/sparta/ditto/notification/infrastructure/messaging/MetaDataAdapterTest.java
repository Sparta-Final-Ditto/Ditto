package com.sparta.ditto.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetaDataAdapter - buildChatMetaData null 포함 직렬화 검증")
class MetaDataAdapterTest {

    private MetaDataAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MetaDataAdapter(new ObjectMapper());
    }

    @Test
    @DisplayName("senderProfileImageUrl이 null이어도 JSON 출력에 senderProfileImageUrl 필드가 포함된다")
    void buildChatMetaData_nullProfileImageUrl_fieldIncludedInJson() throws Exception {
        String json = adapter.buildChatMetaData("room-123", "짱구", null);

        ObjectMapper om = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = om.readTree(json);

        assertThat(node.has("senderProfileImageUrl")).isTrue();
        assertThat(node.get("senderProfileImageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("buildChatMetaData는 roomId, senderNickname, senderProfileImageUrl을 모두 포함한다")
    void buildChatMetaData_allFieldsPresent() throws Exception {
        String json = adapter.buildChatMetaData("room-456", "짱아", "https://cdn.example.com/img.png");

        ObjectMapper om = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = om.readTree(json);

        assertThat(node.get("roomId").asText()).isEqualTo("room-456");
        assertThat(node.get("senderNickname").asText()).isEqualTo("짱아");
        assertThat(node.get("senderProfileImageUrl").asText()).isEqualTo("https://cdn.example.com/img.png");
    }
}