package com.opencsms.ocpp.config;

import com.opencsms.ocpp.handler.OcppWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for OCPP endpoints.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OcppWebSocketHandler ocppWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // OCPP 1.6 endpoint
        registry.addHandler(ocppWebSocketHandler, "/ocpp/{stationId}")
            .setAllowedOrigins("*") // In production, restrict this to known domains
            .addInterceptors(new OcppHandshakeInterceptor());

        // OCPP 2.0.1 endpoint  
        registry.addHandler(ocppWebSocketHandler, "/ocpp2/{stationId}")
            .setAllowedOrigins("*")
            .addInterceptors(new OcppHandshakeInterceptor());
    }
}