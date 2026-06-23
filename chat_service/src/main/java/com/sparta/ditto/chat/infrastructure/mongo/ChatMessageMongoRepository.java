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


    // 나간 사용자 - before 없음: joinedAt 이후 ~ lastVisible 이하 최신 N개 (DESC)
    @Query(value = "{ 'room_id': ?0, 'created_at': { '$gte': ?1 }, "
            + "'$or': [ { 'created_at': { '$lt': ?2 } }, "
            + "{ 'created_at': ?2, '_id': { '$lte': ?3 } } ] }",
            sort = "{ 'created_at': -1, '_id': -1 }")
    List<ChatMessageDocument> findLatestWithinRange(
            UUID roomId, Instant joinedAt,
            Instant upperCreatedAt, String upperMessageId, Limit limit);

    // 나간 사용자 - before 커서: joinedAt 이후 ~ 커서 미만 (DESC)
    @Query(value = "{ 'room_id': ?0, 'created_at': { '$gte': ?1 }, "
            + "'$or': [ { 'created_at': { '$lt': ?2 } }, "
            + "{ 'created_at': ?2, '_id': { '$lt': ?3 } } ] }",
            sort = "{ 'created_at': -1, '_id': -1 }")
    List<ChatMessageDocument> findBeforeCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant cursorCreatedAt, String cursorMessageId, Limit limit);

    // 나간 사용자 - after 커서: joinedAt 이후 ~ lastVisible 이하, 커서 초과 (ASC)
    @Query(value = "{ 'room_id': ?0, 'created_at': { '$gte': ?1 }, '$and': [ "
            + "{ '$or': [ { 'created_at': { '$gt': ?2 } }, "
            + "{ 'created_at': ?2, '_id': { '$gt': ?3 } } ] }, "
            + "{ '$or': [ { 'created_at': { '$lt': ?4 } }, "
            + "{ 'created_at': ?4, '_id': { '$lte': ?5 } } ] } ] }",
            sort = "{ 'created_at': 1, '_id': 1 }")
    List<ChatMessageDocument> findAfterCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant afterCreatedAt, String afterMessageId,
            Instant upperCreatedAt, String upperMessageId, Limit limit);
}
