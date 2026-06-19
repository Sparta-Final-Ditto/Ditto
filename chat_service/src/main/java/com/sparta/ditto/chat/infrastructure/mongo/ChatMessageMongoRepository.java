package com.sparta.ditto.chat.infrastructure.mongo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface ChatMessageMongoRepository extends MongoRepository<ChatMessageDocument, String> {

    // messageId 단건 조회

    // messageId(=_id)로 단건 조회
    Optional<ChatMessageDocument> findByMessageId(String messageId);

    // 방 소속 검증 포함 단건 조회 (메시지 삭제 권한 검증/상세)
    Optional<ChatMessageDocument> findByMessageIdAndRoomId(String messageId, UUID roomId);

    // roomId 기준 메시지 조회 (cursor 페이징)

    // 최초 진입: 최신 N개 조회. 서비스에서 ASC로 재정렬해 응답한다.
    @Query(value = "{ 'room_id': ?0 }", sort = "{ 'created_at': -1, '_id': -1 }")
    List<ChatMessageDocument> findLatestByRoomId(UUID roomId, Limit limit);

    // before: 특정 메시지 이전 메시지 조회. DESC로 가져와 서비스에서 ASC 재정렬.
    @Query(value = "{ 'room_id': ?0, '$or': [ { 'created_at': { '$lt': ?1 } }, "
            + "{ 'created_at': ?1, '_id': { '$lt': ?2 } } ] }",
            sort = "{ 'created_at': -1, '_id': -1 }")
    List<ChatMessageDocument> findBeforeCursor(UUID roomId, Instant cursorCreatedAt,
                                               String cursorMessageId, Limit limit);

    // after: 특정 메시지 이후 메시지 조회. 재연결 누락 동기화용, 이미 ASC.
    @Query(value = "{ 'room_id': ?0, '$or': [ { 'created_at': { '$gt': ?1 } }, "
            + "{ 'created_at': ?1, '_id': { '$gt': ?2 } } ] }",
            sort = "{ 'created_at': 1, '_id': 1 }")
    List<ChatMessageDocument> findAfterCursor(UUID roomId, Instant cursorCreatedAt,
                                              String cursorMessageId, Limit limit);

    // soft delete
    @Query("{ '_id': ?0, 'room_id': ?1, 'deleted_at': null }")
    @Update("{ '$set': { 'deleted_at': ?2 } }")
    long markDeletedByMessageIdAndRoomId(String messageId, UUID roomId, Instant deletedAt);
}