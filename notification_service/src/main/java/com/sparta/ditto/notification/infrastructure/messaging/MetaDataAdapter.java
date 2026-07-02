package com.sparta.ditto.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MetaDataAdapter implements MetaDataPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String buildPostMetaData(String postId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("postId", postId);
        return toJson(meta);
    }

    @Override
    public String buildChatMetaData(String roomId, String senderNickname, String senderProfileImageUrl) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roomId", roomId);
        meta.put("senderNickname", senderNickname);
        meta.put("senderProfileImageUrl", senderProfileImageUrl);
        return toJson(meta);
    }

    @Override
    public String extractRoomId(String metaData) {
        if (metaData == null || metaData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(metaData).path("roomId").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("메타데이터 직렬화 실패", e);
        }
    }
}