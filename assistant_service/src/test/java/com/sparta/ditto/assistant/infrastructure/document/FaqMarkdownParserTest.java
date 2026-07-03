package com.sparta.ditto.assistant.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqMarkdownParserTest {

    private final FaqMarkdownParser parser = new FaqMarkdownParser();

    @Test
    @DisplayName("parse()는 '## id: title' + Q/A 형식의 항목들을 모두 파싱한다")
    void parse_parsesAllEntries() {
        String markdown = """
                ## faq-001: 회원가입 방법

                Q: 회원가입은 어떻게 하나요?
                A: 이메일로 가입합니다.

                ## faq-002: 매칭 횟수 제한

                Q: 매칭은 몇 번 할 수 있나요?
                A: 하루 1회입니다.
                """;

        List<FaqItem> items = parser.parse(markdown);

        assertThat(items).hasSize(2);
        assertThat(items.get(0))
                .isEqualTo(new FaqItem("faq-001", "회원가입 방법", "회원가입은 어떻게 하나요?", "이메일로 가입합니다."));
        assertThat(items.get(1).id()).isEqualTo("faq-002");
    }

    @Test
    @DisplayName("parse()는 형식에 맞는 항목이 없으면 빈 리스트를 반환한다")
    void parse_returnsEmptyList_whenNoEntriesMatch() {
        List<FaqItem> items = parser.parse("아무 형식도 없는 텍스트");

        assertThat(items).isEmpty();
    }
}
