package com.sparta.ditto.match.infrastructure.Llm;

import com.sparta.ditto.match.application.port.LlmPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OllamaAdapter implements LlmPort {

    private final ChatClient chatClient;

    @Override
    public String generate(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
