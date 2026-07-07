package com.sparta.ditto.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator {

    public UUID generate() {
        return UuidCreator.getTimeOrderedEpochPlus1();
    }

    public String generateAsString() {
        return generate().toString();
    }
}
