package com.opencsms.ocpp.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsms.ocpp.message.common.OcppMessageType;
import com.opencsms.ocpp.message.v16.request.BootNotificationRequest;
import com.opencsms.ocpp.message.v16.request.HeartbeatRequest;
import com.opencsms.ocpp.message.v16.request.StatusNotificationRequest;
import com.opencsms.ocpp.message.v16.response.BootNotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OCPP message parser.
 */
public class OcppMessageParserTest {

    private OcppMessageParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        parser = new OcppMessageParser(objectMapper);
    }

    @Test
    void testParseOcpp16BootNotificationRequest() throws Exception {
        String rawMessage = "[2,\"boot-001\",\"BootNotification\"," +
            "{\"chargePointModel\":\"Test Model\",\"chargePointVendor\":\"Test Vendor\"," +
            "\"firmwareVersion\":\"1.0.0\"}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "1.6");

        assertEquals(OcppMessageType.CALL, parsed.getMessageType());
        assertEquals("boot-001", parsed.getMessageId());
        assertEquals("BootNotification", parsed.getAction());
        assertEquals("1.6", parsed.getOcppVersion());
        assertTrue(parsed.isCall());
        assertTrue(parsed.isOcpp16());

        BootNotificationRequest request = parsed.getPayloadAs(BootNotificationRequest.class);
        assertEquals("Test Model", request.getChargePointModel());
        assertEquals("Test Vendor", request.getChargePointVendor());
        assertEquals("1.0.0", request.getFirmwareVersion());
    }

    @Test
    void testParseOcpp16HeartbeatRequest() throws Exception {
        String rawMessage = "[2,\"heartbeat-001\",\"Heartbeat\",{}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "1.6");

        assertEquals(OcppMessageType.CALL, parsed.getMessageType());
        assertEquals("heartbeat-001", parsed.getMessageId());
        assertEquals("Heartbeat", parsed.getAction());
        assertTrue(parsed.isCall());
        
        HeartbeatRequest request = parsed.getPayloadAs(HeartbeatRequest.class);
        assertNotNull(request);
    }

    @Test
    void testParseOcpp16StatusNotificationRequest() throws Exception {
        String rawMessage = "[2,\"status-001\",\"StatusNotification\"," +
            "{\"connectorId\":1,\"errorCode\":\"NoError\",\"status\":\"Available\"," +
            "\"timestamp\":\"2023-12-01T10:00:00Z\"}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "1.6");

        assertEquals(OcppMessageType.CALL, parsed.getMessageType());
        assertEquals("status-001", parsed.getMessageId());
        assertEquals("StatusNotification", parsed.getAction());
        
        StatusNotificationRequest request = parsed.getPayloadAs(StatusNotificationRequest.class);
        assertEquals(Integer.valueOf(1), request.getConnectorId());
        assertEquals(StatusNotificationRequest.ChargePointErrorCode.NoError, request.getErrorCode());
        assertEquals(StatusNotificationRequest.ChargePointStatus.Available, request.getStatus());
    }

    @Test
    void testParseCallResultMessage() throws Exception {
        String rawMessage = "[3,\"boot-001\"," +
            "{\"status\":\"Accepted\",\"currentTime\":\"2023-12-01T10:00:00Z\",\"interval\":300}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "1.6");

        assertEquals(OcppMessageType.CALL_RESULT, parsed.getMessageType());
        assertEquals("boot-001", parsed.getMessageId());
        assertNull(parsed.getAction());
        assertTrue(parsed.isCallResult());
    }

    @Test
    void testParseCallErrorMessage() throws Exception {
        String rawMessage = "[4,\"test-001\",\"NotSupported\"," +
            "\"Action not supported\",{}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "1.6");

        assertEquals(OcppMessageType.CALL_ERROR, parsed.getMessageType());
        assertEquals("test-001", parsed.getMessageId());
        assertEquals("NotSupported", parsed.getAction());
        assertTrue(parsed.isCallError());

        OcppMessageParser.OcppErrorResponse error = parsed.getPayloadAs(OcppMessageParser.OcppErrorResponse.class);
        assertEquals("NotSupported", error.getErrorCode());
        assertEquals("Action not supported", error.getErrorDescription());
    }

    @Test
    void testCreateResponseMessage() throws Exception {
        ParsedOcppMessage request = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("boot-001")
            .action("BootNotification")
            .ocppVersion("1.6")
            .build();

        BootNotificationResponse response = new BootNotificationResponse(
            BootNotificationResponse.RegistrationStatus.Accepted,
            java.time.Instant.now(),
            300
        );

        String responseJson = parser.createResponseMessage(request, response);
        assertNotNull(responseJson);
        assertTrue(responseJson.startsWith("[3,\"boot-001\","));
    }

    @Test
    void testCreateErrorMessage() throws Exception {
        String errorJson = parser.createErrorMessage("test-001", 
            com.opencsms.ocpp.message.common.OcppErrorCode.NOT_SUPPORTED, 
            "Action not supported", null);

        assertNotNull(errorJson);
        assertTrue(errorJson.contains("NotSupported"));
        assertTrue(errorJson.contains("test-001"));
        assertTrue(errorJson.contains("Action not supported"));
    }

    @Test
    void testParseInvalidMessage() {
        String invalidMessage = "[2]"; // Too few elements

        assertThrows(OcppMessageParser.OcppMessageParseException.class, () -> {
            parser.parseMessage(invalidMessage, "1.6");
        });
    }

    @Test
    void testParseOcpp201BootNotification() throws Exception {
        String rawMessage = "[2,\"boot-001\",\"BootNotification\"," +
            "{\"chargingStation\":{\"model\":\"Test Model\",\"vendorName\":\"Test Vendor\"}," +
            "\"reason\":\"PowerUp\"}]";

        ParsedOcppMessage parsed = parser.parseMessage(rawMessage, "2.0.1");

        assertEquals(OcppMessageType.CALL, parsed.getMessageType());
        assertEquals("boot-001", parsed.getMessageId());
        assertEquals("BootNotification", parsed.getAction());
        assertEquals("2.0.1", parsed.getOcppVersion());
        assertTrue(parsed.isOcpp201());

        com.opencsms.ocpp.message.v201.request.BootNotificationRequest request = 
            parsed.getPayloadAs(com.opencsms.ocpp.message.v201.request.BootNotificationRequest.class);
        assertEquals("Test Model", request.getChargingStation().getModel());
        assertEquals("Test Vendor", request.getChargingStation().getVendorName());
    }
}