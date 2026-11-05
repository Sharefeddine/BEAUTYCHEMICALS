package com.chemmanager.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

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
        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addSerializer(LocalDate.class, new LocalDateSerializer());
        timeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer());
        timeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());

        return new ObjectMapper()
                .registerModule(timeModule)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }
}
