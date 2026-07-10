package com.sparta.ditto.match.application.port;

// 어떤 LLM이든 교체 가능하도록 인터페이스로 추상화

public interface LlmPort {
    String generate(String prompt);
}
