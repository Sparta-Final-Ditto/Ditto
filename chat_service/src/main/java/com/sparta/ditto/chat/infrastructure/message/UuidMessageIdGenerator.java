package com.sparta.ditto.chat.infrastructure.message;

import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidMessageIdGenerator implements MessageIdGenerator {

    @Override
    public String generate() {
        // TODO: 공통모듈에 UUID v7 generator 적용 후 교체
        return UUID.randomUUID().toString();
    }
}