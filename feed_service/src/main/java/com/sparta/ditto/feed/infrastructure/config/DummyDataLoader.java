package com.sparta.ditto.feed.infrastructure.config;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.MediaType;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("local") // 안전장치: local 환경에서만 실행되도록 설정
@RequiredArgsConstructor
public class DummyDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DummyDataLoader.class);
    private final PostRepository postRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"); // 테스트용 UUID (아무거나 상관없음)

        log.info("========== DummyDataLoader: 피드 테스트용 더미 데이터 50개 생성을 시작합니다 ==========");

        for (int i = 1; i <= 50; i++) {
            Post post = new Post(
                    userId,
                    "tester_" + i,
                    "성능 테스트용 게시글 " + i + "번입니다. N+1 문제를 확인해보세요!",
                    "서울특별시 강남구",
                    37.5665,
                    126.9780,
                    Visibility.PUBLIC,
                    true
            );

            // 각 게시글마다 태그 3개씩 추가
            post.getTags().add(new PostTag(post, "테스트"));
            post.getTags().add(new PostTag(post, "JMeter"));
            post.getTags().add(new PostTag(post, "성능개선"));

            // 각 게시글마다 사진 2장씩 추가
            post.addMedia(new PostMedia(post, "test/image" + i + "_1.jpg", MediaType.IMAGE, 1));
            post.addMedia(new PostMedia(post, "test/image" + i + "_2.jpg", MediaType.IMAGE, 2));

            postRepository.save(post);
        }

        log.info("========== DummyDataLoader: 더미 데이터 생성이 완료되었습니다 ==========");
    }
}
