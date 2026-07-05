package com.sparta.ditto.assistant.infrastructure.document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** FAQ/정책 마크다운을 FaqItem 목록으로 파싱 */
@Component
public class FaqMarkdownParser {

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "^## (?<id>\\S+): (?<title>.+)$\\R+^Q: (?<question>.+)$\\R+^A: (?<answer>.+)$",
            Pattern.MULTILINE
    );

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
        return items;
    }
}
