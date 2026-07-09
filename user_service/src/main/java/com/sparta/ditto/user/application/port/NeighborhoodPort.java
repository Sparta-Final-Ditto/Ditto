package com.sparta.ditto.user.application.port;

/**
 * 역지오코딩(위경도 → 동네명 변환) 포트 인터페이스.
 * 구현체(NeighborhoodAdapter)는 Redis 캐시를 먼저 조회하고 미스 시 Kakao Local API를 호출한다.
 * API 호출 실패 시 null을 반환하며, 사용자 위치는 neighborhood=null 상태로 정상 저장된다.
 */
public interface NeighborhoodPort {
    String resolveNeighborhood(double latitude, double longitude);
}
