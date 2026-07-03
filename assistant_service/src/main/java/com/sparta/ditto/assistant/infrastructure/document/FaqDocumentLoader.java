package com.sparta.ditto.assistant.infrastructure.document;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/** 기동 시 FAQ 문서를 청킹해 vector_store에 적재 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqDocumentLoader implements ApplicationRunner {

    private static final String FAQ_RESOURCE_PATH = "faq/faq.md";

    private final VectorStore vectorStore;
    private final AssistantDocumentChunker chunker;
    private final FaqMarkdownParser faqMarkdownParser;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String markdown = StreamUtils.copyToString(
                new ClassPathResource(FAQ_RESOURCE_PATH).getInputStream(), StandardCharsets.UTF_8);

        List<FaqItem> faqItems = faqMarkdownParser.parse(markdown);

        List<Document> documents = faqItems.stream()
                .map(chunker::chunk)
                .toList();

        vectorStore.add(documents);
        log.info("FAQ 문서 {}건을 벡터 저장소에 적재했습니다.", documents.size());
    }
}
