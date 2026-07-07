package com.sparta.ditto.assistant.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** evaluation/golden-set.md를 GoldenSetItem 목록으로 파싱 */
public final class GoldenSetLoader {

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "^## (?<id>\\S+): (?<category>.+)$\\R+"
                    + "^Q: (?<question>.+)$\\R+"
                    + "^Expected: (?<expected>.+)$\\R+"
                    + "^Note: (?<note>.+)$",
            Pattern.MULTILINE
    );

    private GoldenSetLoader() {
    }

    public static List<GoldenSetItem> parse(String markdown) {
        List<GoldenSetItem> items = new ArrayList<>();
        Matcher matcher = ENTRY_PATTERN.matcher(markdown);
        while (matcher.find()) {
            items.add(new GoldenSetItem(
                    matcher.group("id"),
                    matcher.group("category"),
                    matcher.group("question"),
                    matcher.group("expected"),
                    matcher.group("note")
            ));
        }
        return items;
    }
}
