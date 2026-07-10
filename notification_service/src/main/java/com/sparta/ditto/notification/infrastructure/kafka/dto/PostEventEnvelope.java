package com.sparta.ditto.notification.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PostEventEnvelope(
        String eventId,
        String eventType,
        String occurredAt,
        JsonNode payload
) {}
