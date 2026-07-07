package com.sparta.ditto.assistant.infrastructure.document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** FAQ/정책 마크다운을 FaqItem 목록으로 파싱 */
@Slf4j
@Component
public class FaqMarkdownParser {

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "^## (?<id>\\S+): (?<title>.+)$\\R+"
                    + "^Q: (?<question>[\\s\\S]+?)$\\R+"
                    + "^A: (?<answer>[\\s\\S]+?)(?=\\R+^## \\S+:|\\R*\\z)",
            Pattern.MULTILINE
    );

    private static final Pattern HEADER_PATTERN = Pattern.compile("^## \\S+:", Pattern.MULTILINE);

    public List<FaqItem> parse(String markdown) {
        List<FaqItem> items = new ArrayList<>();
        Matcher matcher = ENTRY_PATTERN.matcher(markdown);
        while (matcher.find()) {
            items.add(new FaqItem(
                    matcher.group("id"),
                    matcher.group("title"),
                    matcher.group("question"),
                    matcher.group("answer")
            ));
        }
        warnIfEntriesDropped(markdown, items.size());
        return items;
    }

    private void warnIfEntriesDropped(String markdown, int parsedCount) {
        int headerCount = 0;
        Matcher headerMatcher = HEADER_PATTERN.matcher(markdown);
        while (headerMatcher.find()) {
            headerCount++;
        }
        if (headerCount != parsedCount) {
            log.warn("'## id: title' 헤더 {}개 중 {}개만 파싱되었습니다. "
                            + "Q:/A: 형식이 맞지 않는 항목이 있는지 확인하세요.",
                    headerCount, parsedCount);
        }
    }
}
