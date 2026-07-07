package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.service.BlockCheckService;
import com.sparta.ditto.feed.application.service.FeedService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 피드 3종의 차단 필터를 조율하는 퍼사드(트랜잭션 없음).
 *
 * <p>차단 목록 조회(Feign I/O)를 DB 트랜잭션 밖에서 먼저 수행한 뒤, 차단 ID를 파라미터로 전달한다.
 * 랜덤 피드는 {@link FeedService}로 바로 위임하고, 매칭/팔로우 피드는 외부 소스 호출과
 * Resilience4j를 트랜잭션 밖에서 처리하는 {@link FeedSourceFacade}로 위임한다.
 * 진입 메서드(무 트랜잭션)와 {@code @Transactional} 메서드를 서로 다른 빈에 두어
 * self-invocation을 피한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FeedFacade {

    private final BlockCheckService blockCheckService;
    private final FeedService feedService;
    private final FeedSourceFacade feedSourceFacade;

    public FeedResult getRandomFeed(GetRandomFeedQuery query) {
        List<UUID> blockedIds = blockCheckService.blockedUserIds(query.userId());
        return feedService.getRandomFeed(query, blockedIds);
    }

    public FeedResult getFollowFeed(GetFollowFeedQuery query) {
        List<UUID> blockedIds = blockCheckService.blockedUserIds(query.userId());
        return feedSourceFacade.getFollowFeed(query, blockedIds);
    }

    public FeedResult getMatchFeed(GetMatchFeedQuery query) {
        List<UUID> blockedIds = blockCheckService.blockedUserIds(query.userId());
        return feedSourceFacade.getMatchFeed(query, blockedIds);
    }
}
