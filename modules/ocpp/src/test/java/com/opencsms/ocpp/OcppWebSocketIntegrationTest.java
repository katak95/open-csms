package com.opencsms.ocpp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsms.ocpp.session.OcppSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OCPP WebSocket infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
public class OcppWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OcppSessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testOcppWebSocketConnection() throws Exception {
        String stationId = "TEST_STATION_001";
        String wsUrl = String.format("ws://localhost:%d/ocpp/%s", port, stationId);
        
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        
        WebSocketSession session = null;
        
        try {
            WebSocketClient client = new StandardWebSocketClient();
            
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) {
                    log.info("WebSocket connection established");
                    connectionLatch.countDown();
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                    log.info("Received message: {}", message.getPayload());
                    messageLatch.countDown();
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) {
                    log.error("Transport error", exception);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                    log.info("Connection closed: {}", closeStatus);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };
            
            session = client.doHandshake(handler, "ws://localhost:" + port + "/ocpp/" + stationId, URI.create(wsUrl)).get();
            
            // Wait for connection
            assertTrue(connectionLatch.await(5, TimeUnit.SECONDS), "Connection should be established");
            
            // Send BootNotification
            String bootNotification = objectMapper.writeValueAsString(new Object[]{
                2, // CALL
                "boot-001", // Message ID
                "BootNotification", // Action
                new BootNotificationRequest("Test Station", "TestVendor", "1.0") // Payload
            });
            
            session.sendMessage(new TextMessage(bootNotification));
            
            // Wait for response
            assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Should receive response");
            
            // Verify session is registered
            assertEquals(1, sessionManager.getActiveSessionCount());
            
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BootNotificationRequest {
        private String chargePointModel;
        private String chargePointVendor;
        private String firmwareVersion;
    }
}