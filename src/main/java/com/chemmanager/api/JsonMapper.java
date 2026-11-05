package com.chemmanager.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson configuration. The API speaks snake_case JSON (natural for PHP/MySQL),
 * so the Java records are mapped automatically and dates stay ISO-8601.
 */
public final class JsonMapper {

    private static final ObjectMapper MAPPER = create();

    private JsonMapper() {
    }

    public static ObjectMapper get() {
        return MAPPER;
    }

    private static ObjectMapper create() {
        // JavaTimeModule registers the ISO-8601 serialisers for LocalDate / LocalDateTime.
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }
}
