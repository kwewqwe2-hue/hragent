package com.hragent.hragentv1.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {
    @Test
    void serializesLocalDateAsIsoText() throws Exception {
        ObjectMapper objectMapper = new AppConfig().jacksonBuilder().build();

        assertEquals("\"2026-07-31\"", objectMapper.writeValueAsString(LocalDate.of(2026, 7, 31)));
    }
}
