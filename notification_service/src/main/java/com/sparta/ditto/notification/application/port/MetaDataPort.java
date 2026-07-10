package com.sparta.ditto.notification.application.port;

public interface MetaDataPort {

    String buildPostMetaData(String postId);

    String buildChatMetaData(String roomId, String senderNickname, String senderProfileImageUrl);

    String extractRoomId(String metaData);
}
