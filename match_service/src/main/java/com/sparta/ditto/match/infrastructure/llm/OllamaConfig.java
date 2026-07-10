package com.sparta.ditto.match.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Bean
    public ChatClient chatClient(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
