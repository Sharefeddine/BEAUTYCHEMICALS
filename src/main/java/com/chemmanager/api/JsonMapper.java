package com.chemmanager.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson configuration. The API speaks lowerCamelCase JSON, exactly like the MySQL
 * columns (see database/README.md), so a field name is written identically in SQL, PHP and Java
 * and no translation layer is needed. Dates stay ISO-8601.
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
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }
}
