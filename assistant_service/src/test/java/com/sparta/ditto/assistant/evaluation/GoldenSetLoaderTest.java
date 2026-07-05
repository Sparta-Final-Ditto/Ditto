package com.sparta.ditto.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class GoldenSetLoaderTest {

    @Test
    @DisplayName("parse()는 '## golden-id: category' + Q/Expected/Note 형식의 항목들을 모두 파싱한다")
    void parse_parsesAllEntries() {
        String markdown = """
                ## golden-001: FAQ

                Q: 매칭은 하루에 몇 번 가능해요?
                Expected: faq-003
                Note: -

                ## golden-028: TRAP

                Q: 매칭 성공 확률은 몇 %인가요?
                Expected: NONE
                Note: 순수 hallucination trap
                """;

        List<GoldenSetItem> items = GoldenSetLoader.parse(markdown);

        assertThat(items).hasSize(2);
        assertThat(items.get(0)).isEqualTo(
                new GoldenSetItem("golden-001", "FAQ", "매칭은 하루에 몇 번 가능해요?", "faq-003", "-"));
        assertThat(items.get(0).isTrap()).isFalse();
        assertThat(items.get(1).isTrap()).isTrue();
    }

    @Test
    @DisplayName("실제 evaluation/golden-set.md는 정확히 30문항, 그중 4건이 trap이다")
    void parse_realGoldenSetFile_has30ItemsWith4Traps() throws IOException {
        String markdown = StreamUtils.copyToString(
                new ClassPathResource("evaluation/golden-set.md").getInputStream(),
                StandardCharsets.UTF_8);

        List<GoldenSetItem> items = GoldenSetLoader.parse(markdown);

        assertThat(items).hasSize(30);
        assertThat(items.stream().filter(GoldenSetItem::isTrap)).hasSize(4);
        assertThat(items.stream().map(GoldenSetItem::id)).doesNotHaveDuplicates();
    }
}
