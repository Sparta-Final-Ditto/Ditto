package com.sparta.ditto.chat.infrastructure.message;

import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UuidMessageIdGenerator implements MessageIdGenerator {

    private final UuidV7Generator uuidV7Generator;

    @Override
    public String generate() {
        return uuidV7Generator.generateAsString();
    }
}
