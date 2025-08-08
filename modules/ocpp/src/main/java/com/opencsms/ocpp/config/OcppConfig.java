package com.opencsms.ocpp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for OCPP module.
 */
@Configuration
@EnableScheduling
public class OcppConfig {

    /**
     * ObjectMapper for OCPP message serialization/deserialization.
     */
    @Bean
    public ObjectMapper ocppObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper for OCPP-specific needs
        objectMapper.findAndRegisterModules();
        return objectMapper;
    }
}