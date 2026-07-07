package com.sparta.ditto.assistant.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class FaqDocumentLoaderTest {

    @Mock
    private VectorStore vectorStore;

    private final AssistantDocumentChunker chunker = new AssistantDocumentChunker();
    private final FaqMarkdownParser faqMarkdownParser = new FaqMarkdownParser();

    @Test
    @DisplayName("run()은 faq/*.md, policy/*.md의 모든 항목을 청킹해 VectorStore에 적재한다")
    void run_loadsAllFaqEntriesIntoVectorStore() throws Exception {
        FaqDocumentLoader loader = new FaqDocumentLoader(vectorStore, chunker, faqMarkdownParser);

        loader.run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("run()은 FAQ/POLICY 각 sourceType별로 현재 존재하지 않는 itemId를 정리하는 delete 필터를 호출한다")
    void run_deletesStaleEntriesPerSourceType() throws Exception {
        FaqDocumentLoader loader = new FaqDocumentLoader(vectorStore, chunker, faqMarkdownParser);

        loader.run(new DefaultApplicationArguments());

        ArgumentCaptor<Filter.Expression> filterCaptor =
                ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore, times(2)).delete(filterCaptor.capture());
        assertThat(filterCaptor.getAllValues())
                .allSatisfy(expr -> assertThat(expr.type()).isEqualTo(Filter.ExpressionType.AND));
    }
}
