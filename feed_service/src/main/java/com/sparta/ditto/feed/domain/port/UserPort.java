package com.sparta.ditto.feed.domain.port;

import java.util.UUID;
/**
 * User Service 닉네임 조회 포트 인터페이스.
 * 게시글 생성 시점의 닉네임을 조회해 posts 테이블에 비정규화 저장한다.
 * 이후 사용자가 닉네임을 변경해도 기존 게시글의 닉네임은 유지된다.
 * 조회 실패 시 null을 반환하며, 게시글은 nickname=null 상태로 정상 저장된다.
 * 구현체(UserAdapter)는 OpenFeign을 사용한다.
 */
public interface UserPort {
    String getNickname(UUID userId);
}