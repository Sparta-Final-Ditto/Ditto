package com.sparta.ditto.assistant.infrastructure.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/** 기동 시 FAQ·정책 문서를 청킹해 vector_store에 적재 */
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
        for (Map.Entry<String, String> location : SOURCE_LOCATIONS.entrySet()) {
            documents.addAll(loadDocuments(location.getKey(), location.getValue()));
        }

        vectorStore.add(documents);
        log.info("FAQ/정책 문서 {}건을 벡터 저장소에 적재했습니다.", documents.size());
    }

    private List<Document> loadDocuments(String locationPattern, String sourceType) throws IOException {
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resourcePatternResolver.getResources(locationPattern)) {
            String markdown = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            List<FaqItem> items = faqMarkdownParser.parse(markdown);
            items.forEach(item -> documents.add(chunker.chunk(item, sourceType)));
        }
        return documents;
    }
}
