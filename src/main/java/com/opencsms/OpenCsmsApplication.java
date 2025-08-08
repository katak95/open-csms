package com.opencsms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Open-CSMS Application - Main Spring Boot Application
 * 
 * Open Source Charging Station Management System designed to compete with 
 * enterprise-grade proprietary solutions while maintaining deployment simplicity.
 * 
 * Key Features:
 * - Complete OCPP 1.6 & 2.0.1 support
 * - OCPI 2.2.1 Hub capabilities (unique in open source)
 * - Multi-tenant architecture with enterprise security
 * - Expert-grade charging station management
 * - Maximum authentication compatibility (RFID to ISO 15118)
 * 
 * Architecture:
 * - Modular hybrid (core monolith + decoupled services)
 * - Multi-database (PostgreSQL + InfluxDB + Redis)
 * - WebSocket for OCPP, REST for OCPI, GraphQL for integrations
 * 
 * @author Open-CSMS Team
 * @version 0.1.0-SNAPSHOT
 * @since 2025-01-07
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ConfigurationPropertiesScan
public class OpenCsmsApplication {

    public static void main(String[] args) {
        // Set default profile if none specified
        System.setProperty("spring.profiles.default", "development");
        
        SpringApplication.run(OpenCsmsApplication.class, args);
    }
}