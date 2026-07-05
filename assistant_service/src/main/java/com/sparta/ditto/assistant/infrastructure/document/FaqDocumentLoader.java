package com.sparta.ditto.assistant.infrastructure.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/** 기동 시 FAQ·정책 문서를 청킹해 vector_store에 적재하고, markdown에서 삭제된 항목의 벡터를 정리한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqDocumentLoader implements ApplicationRunner {

    private static final Map<String, String> SOURCE_LOCATIONS = Map.of(
            "classpath:faq/*.md", "FAQ",
            "classpath:policy/*.md", "POLICY"
    );

    private final VectorStore vectorStore;
    private final AssistantDocumentChunker chunker;
    private final FaqMarkdownParser faqMarkdownParser;
    private final PathMatchingResourcePatternResolver resourcePatternResolver =
            new PathMatchingResourcePatternResolver();

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<Document> documents = new ArrayList<>();
        Map<String, List<String>> currentItemIdsBySourceType = new LinkedHashMap<>();

        for (Map.Entry<String, String> location : SOURCE_LOCATIONS.entrySet()) {
            String sourceType = location.getValue();
            List<FaqItem> items = loadItems(location.getKey());
            items.forEach(item -> documents.add(chunker.chunk(item, sourceType)));
            currentItemIdsBySourceType.put(sourceType, items.stream().map(FaqItem::id).toList());
        }

        vectorStore.add(documents);
        log.info("FAQ/정책 문서 {}건을 벡터 저장소에 적재했습니다.", documents.size());

        currentItemIdsBySourceType.forEach(this::deleteStaleEntries);
    }

    private List<FaqItem> loadItems(String locationPattern) throws IOException {
        List<FaqItem> items = new ArrayList<>();
        for (Resource resource : resourcePatternResolver.getResources(locationPattern)) {
            String markdown = StreamUtils.copyToString(
                    resource.getInputStream(), StandardCharsets.UTF_8);
            items.addAll(faqMarkdownParser.parse(markdown));
        }
        return items;
    }

    // 원본 markdown에서 더 이상 존재하지 않는 itemId의 벡터를 삭제한다.
    private void deleteStaleEntries(String sourceType, List<String> currentItemIds) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        Filter.Expression staleFilter = currentItemIds.isEmpty()
                ? filterBuilder.eq("sourceType", sourceType).build()
                : filterBuilder.and(
                        filterBuilder.eq("sourceType", sourceType),
                        filterBuilder.nin("itemId", new ArrayList<Object>(currentItemIds))
                ).build();
        vectorStore.delete(staleFilter);
    }
}
