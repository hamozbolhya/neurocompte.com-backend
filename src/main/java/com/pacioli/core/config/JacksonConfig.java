package com.pacioli.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pacioli.core.JsonParser.FlexibleLocalDateDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.text.SimpleDateFormat;
import java.time.LocalDate;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        return objectMapper;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // Add deserializer for LocalDate globally
            builder.deserializerByType(LocalDate.class, new FlexibleLocalDateDeserializer());

            // Or use a simpler approach with pattern specification
            builder.simpleDateFormat("yyyy-MM-dd");
            builder.dateFormat(new SimpleDateFormat("yyyy-MM-dd"));

            // Add a module that handles Java 8 date/time types
            builder.modules(new JavaTimeModule());
        };
    }
}
