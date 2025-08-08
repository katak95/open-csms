package com.opencsms.ocpp.message;

import com.opencsms.ocpp.message.common.OcppErrorCode;
import com.opencsms.ocpp.message.common.OcppMessageType;
import com.opencsms.ocpp.message.v16.request.BootNotificationRequest;
import com.opencsms.ocpp.message.v16.request.StartTransactionRequest;
import com.opencsms.ocpp.message.v16.request.StatusNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OCPP message validator.
 */
public class OcppMessageValidatorTest {

    private OcppMessageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OcppMessageValidator();
    }

    @Test
    void testValidBootNotificationRequest16() {
        BootNotificationRequest request = new BootNotificationRequest();
        request.setChargePointModel("Test Model");
        request.setChargePointVendor("Test Vendor");
        request.setFirmwareVersion("1.0.0");

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("boot-001")
            .action("BootNotification")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidBootNotificationRequest16_MissingModel() {
        BootNotificationRequest request = new BootNotificationRequest();
        request.setChargePointVendor("Test Vendor");
        request.setFirmwareVersion("1.0.0");

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("boot-001")
            .action("BootNotification")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, result.getErrorCode());
        assertTrue(result.getErrorDescription().contains("chargePointModel"));
    }

    @Test
    void testInvalidBootNotificationRequest16_ModelTooLong() {
        BootNotificationRequest request = new BootNotificationRequest();
        request.setChargePointModel("A".repeat(51)); // Too long
        request.setChargePointVendor("Test Vendor");
        request.setFirmwareVersion("1.0.0");

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("boot-001")
            .action("BootNotification")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, result.getErrorCode());
        assertTrue(result.getErrorDescription().contains("maximum length"));
    }

    @Test
    void testValidStatusNotificationRequest16() {
        StatusNotificationRequest request = new StatusNotificationRequest();
        request.setConnectorId(1);
        request.setErrorCode(StatusNotificationRequest.ChargePointErrorCode.NoError);
        request.setStatus(StatusNotificationRequest.ChargePointStatus.Available);
        request.setTimestamp(Instant.now());

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("status-001")
            .action("StatusNotification")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidStatusNotificationRequest16_NegativeConnectorId() {
        StatusNotificationRequest request = new StatusNotificationRequest();
        request.setConnectorId(-1);
        request.setErrorCode(StatusNotificationRequest.ChargePointErrorCode.NoError);
        request.setStatus(StatusNotificationRequest.ChargePointStatus.Available);

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("status-001")
            .action("StatusNotification")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, result.getErrorCode());
        assertTrue(result.getErrorDescription().contains("connectorId"));
    }

    @Test
    void testValidStartTransactionRequest16() {
        StartTransactionRequest request = new StartTransactionRequest();
        request.setConnectorId(1);
        request.setIdTag("RFID123456");
        request.setMeterStart(1000);
        request.setTimestamp(Instant.now());

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("start-001")
            .action("StartTransaction")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidStartTransactionRequest16_IdTagTooLong() {
        StartTransactionRequest request = new StartTransactionRequest();
        request.setConnectorId(1);
        request.setIdTag("A".repeat(21)); // Too long
        request.setMeterStart(1000);
        request.setTimestamp(Instant.now());

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("start-001")
            .action("StartTransaction")
            .ocppVersion("1.6")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.PROPERTY_CONSTRAINT_VIOLATION, result.getErrorCode());
        assertTrue(result.getErrorDescription().contains("idTag"));
    }

    @Test
    void testValidHeartbeatRequest16() {
        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("heartbeat-001")
            .action("Heartbeat")
            .ocppVersion("1.6")
            .payload(new com.opencsms.ocpp.message.v16.request.HeartbeatRequest())
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isValid());
    }

    @Test
    void testUnsupportedAction() {
        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("test-001")
            .action("UnsupportedAction")
            .ocppVersion("1.6")
            .payload(new Object())
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.NOT_SUPPORTED, result.getErrorCode());
    }

    @Test
    void testUnsupportedOcppVersion() {
        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("test-001")
            .action("BootNotification")
            .ocppVersion("3.0")
            .payload(new Object())
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isInvalid());
        assertEquals(OcppErrorCode.NOT_SUPPORTED, result.getErrorCode());
        assertTrue(result.getErrorDescription().contains("Unsupported OCPP version"));
    }

    @Test
    void testValidOcpp201BootNotification() {
        com.opencsms.ocpp.message.v201.request.BootNotificationRequest request = 
            new com.opencsms.ocpp.message.v201.request.BootNotificationRequest();
        
        com.opencsms.ocpp.message.v201.request.BootNotificationRequest.ChargingStation station = 
            new com.opencsms.ocpp.message.v201.request.BootNotificationRequest.ChargingStation();
        station.setModel("Test Model");
        station.setVendorName("Test Vendor");
        
        request.setChargingStation(station);
        request.setReason(com.opencsms.ocpp.message.v201.request.BootNotificationRequest.BootReason.PowerUp);

        ParsedOcppMessage message = ParsedOcppMessage.builder()
            .messageType(OcppMessageType.CALL)
            .messageId("boot-001")
            .action("BootNotification")
            .ocppVersion("2.0.1")
            .payload(request)
            .build();

        OcppMessageValidator.ValidationResult result = validator.validate(message);
        assertTrue(result.isValid());
    }
}