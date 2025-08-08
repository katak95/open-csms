package com.opencsms.service.ocpp;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.session.MeterValue;
import com.opencsms.service.core.TenantContext;
import com.opencsms.service.session.ChargingSessionService;
import com.opencsms.service.session.exception.SessionNotFoundException;
import com.opencsms.service.ocpp.model.ParsedOcppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service handling OCPP StopTransaction messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StopTransactionService {

    private final ChargingSessionService sessionService;

    /**
     * Process OCPP 1.6 StopTransaction message
     */
    public Map<String, Object> processStopTransaction16(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            
            // Extract parameters
            Integer transactionId = (Integer) payload.get("transactionId");
            String idTag = (String) payload.get("idTag");
            Integer meterStop = (Integer) payload.get("meterStop");
            String timestamp = (String) payload.get("timestamp");
            String reasonString = (String) payload.get("reason");

            log.info("Processing StopTransaction for station {} transaction {} with meterStop {}", 
                    stationSerial, transactionId, meterStop);

            // Find session by transaction ID
            ChargingSession session;
            try {
                session = sessionService.getSessionByTransactionId(transactionId);
            } catch (SessionNotFoundException e) {
                log.warn("Transaction {} not found for StopTransaction", transactionId);
                return createStopTransactionResponse("Invalid", "UnknownTransaction");
            }

            // Validate idTag matches
            if (!session.getOcppIdTag().equals(idTag)) {
                log.warn("IdTag mismatch for transaction {}: expected {}, got {}", 
                        transactionId, session.getOcppIdTag(), idTag);
                return createStopTransactionResponse("Invalid", "InvalidToken");
            }

            // Parse stop reason
            ChargingSession.StopReason stopReason = parseStopReason(reasonString);

            // Process any transaction data (meter values)
            if (payload.containsKey("transactionData")) {
                processTransactionData(session, (List<Map<String, Object>>) payload.get("transactionData"));
            }

            // Stop the session
            ChargingSession stoppedSession = sessionService.stopSession(
                session.getId(), 
                BigDecimal.valueOf(meterStop),
                stopReason
            );

            log.info("Stopped transaction {} for session {} - Energy: {} kWh, Duration: {} min", 
                    transactionId, stoppedSession.getId(), 
                    stoppedSession.getEnergyDeliveredKwh(), stoppedSession.getDurationMinutes());

            return createStopTransactionResponse("Accepted", null);

        } catch (Exception e) {
            log.error("Error processing StopTransaction for station {}: {}", stationSerial, e.getMessage(), e);
            return createStopTransactionResponse("Invalid", "InternalError");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Process OCPP 2.0.1 TransactionEvent with eventType Ended
     */
    public Map<String, Object> processTransactionEventEnded(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            Map<String, Object> transactionInfo = (Map<String, Object>) payload.get("transactionInfo");
            Map<String, Object> idToken = (Map<String, Object>) payload.get("idToken");

            // Extract parameters
            String transactionId = (String) transactionInfo.get("transactionId");
            String idTokenValue = (String) idToken.get("idToken");
            String reasonString = (String) payload.get("reason");

            // Get meter value if present
            BigDecimal meterValue = BigDecimal.ZERO;
            if (payload.containsKey("meterValue")) {
                meterValue = extractMeterValueFromOcpp201(payload);
            }

            log.info("Processing TransactionEvent Ended for station {} transaction {}", 
                    stationSerial, transactionId);

            // Find session by transaction ID (hash of string ID for compatibility)
            ChargingSession session;
            try {
                session = sessionService.getSessionByTransactionId(transactionId.hashCode());
            } catch (SessionNotFoundException e) {
                log.warn("Transaction {} not found for TransactionEvent Ended", transactionId);
                return createTransactionEventResponse("Invalid", "UnknownTransaction");
            }

            // Parse stop reason
            ChargingSession.StopReason stopReason = parseStopReasonOcpp201(reasonString);

            // Stop the session
            ChargingSession stoppedSession = sessionService.stopSession(
                session.getId(), 
                meterValue,
                stopReason
            );

            log.info("Stopped OCPP 2.0.1 transaction {} for session {}", transactionId, stoppedSession.getId());

            return createTransactionEventResponse("Accepted", null);

        } catch (Exception e) {
            log.error("Error processing TransactionEvent Ended for station {}: {}", stationSerial, e.getMessage(), e);
            return createTransactionEventResponse("Invalid", "InternalError");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Process transaction data (meter values during transaction)
     */
    private void processTransactionData(ChargingSession session, List<Map<String, Object>> transactionData) {
        if (transactionData == null || transactionData.isEmpty()) {
            return;
        }

        for (Map<String, Object> data : transactionData) {
            String timestamp = (String) data.get("timestamp");
            List<Map<String, Object>> sampledValues = (List<Map<String, Object>>) data.get("sampledValue");

            Instant meterTimestamp = parseTimestamp(timestamp);

            if (sampledValues != null) {
                for (Map<String, Object> sampledValue : sampledValues) {
                    String value = (String) sampledValue.get("value");
                    String context = (String) sampledValue.get("context");
                    String measurand = (String) sampledValue.get("measurand");
                    String location = (String) sampledValue.get("location");
                    String phase = (String) sampledValue.get("phase");
                    String unit = (String) sampledValue.get("unit");

                    try {
                        MeterValue meterValue = MeterValue.builder()
                            .timestamp(meterTimestamp)
                            .measurand(parseMeasurand(measurand))
                            .value(new BigDecimal(value))
                            .unit(unit)
                            .context(parseContext(context))
                            .location(parseLocation(location))
                            .phase(parsePhase(phase))
                            .build();

                        sessionService.addMeterValue(session.getId(), meterValue);

                    } catch (Exception e) {
                        log.warn("Error processing meter value for session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    private ChargingSession.StopReason parseStopReason(String reason) {
        if (reason == null) return ChargingSession.StopReason.OTHER;
        
        try {
            return ChargingSession.StopReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown stop reason: {}", reason);
            return ChargingSession.StopReason.OTHER;
        }
    }

    private ChargingSession.StopReason parseStopReasonOcpp201(String reason) {
        // OCPP 2.0.1 has different reason codes
        if (reason == null) return ChargingSession.StopReason.OTHER;
        
        switch (reason.toLowerCase()) {
            case "deauthorized":
                return ChargingSession.StopReason.DE_AUTHORIZED;
            case "emergencystop":
                return ChargingSession.StopReason.EMERGENCY_STOP;
            case "evdisconnected":
                return ChargingSession.StopReason.EV_DISCONNECTED;
            case "hardreset":
                return ChargingSession.StopReason.HARD_RESET;
            case "local":
                return ChargingSession.StopReason.LOCAL;
            case "powerloss":
                return ChargingSession.StopReason.POWER_LOSS;
            case "reboot":
                return ChargingSession.StopReason.REBOOT;
            case "remote":
                return ChargingSession.StopReason.REMOTE;
            case "softreset":
                return ChargingSession.StopReason.SOFT_RESET;
            case "unlockcommand":
                return ChargingSession.StopReason.UNLOCK_COMMAND;
            default:
                return ChargingSession.StopReason.OTHER;
        }
    }

    private MeterValue.Measurand parseMeasurand(String measurand) {
        if (measurand == null) return MeterValue.Measurand.ENERGY_ACTIVE_IMPORT_REGISTER;
        
        try {
            return MeterValue.Measurand.valueOf(measurand.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MeterValue.Measurand.ENERGY_ACTIVE_IMPORT_REGISTER;
        }
    }

    private MeterValue.Context parseContext(String context) {
        if (context == null) return MeterValue.Context.SAMPLE_PERIODIC;
        
        try {
            return MeterValue.Context.valueOf(context.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MeterValue.Context.SAMPLE_PERIODIC;
        }
    }

    private MeterValue.Location parseLocation(String location) {
        if (location == null) return MeterValue.Location.OUTLET;
        
        try {
            return MeterValue.Location.valueOf(location.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MeterValue.Location.OUTLET;
        }
    }

    private MeterValue.Phase parsePhase(String phase) {
        if (phase == null) return null;
        
        try {
            return MeterValue.Phase.valueOf(phase.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (Exception e) {
            log.warn("Error parsing timestamp {}: {}", timestamp, e.getMessage());
            return Instant.now();
        }
    }

    private Map<String, Object> createStopTransactionResponse(String status, String statusReason) {
        Map<String, Object> response = Map.of(
            "idTagInfo", Map.of(
                "status", status
            )
        );

        if (statusReason != null) {
            Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");
            idTagInfo.put("statusReason", statusReason);
        }

        return response;
    }

    private Map<String, Object> createTransactionEventResponse(String status, String statusReason) {
        Map<String, Object> response = Map.of(
            "idTokenInfo", Map.of(
                "status", status
            )
        );

        if (statusReason != null) {
            Map<String, Object> idTokenInfo = (Map<String, Object>) response.get("idTokenInfo");
            idTokenInfo.put("statusReason", statusReason);
        }

        return response;
    }

    private BigDecimal extractMeterValueFromOcpp201(Map<String, Object> payload) {
        // OCPP 2.0.1 has complex meter value structure
        // For now, return zero - would need full implementation
        return BigDecimal.ZERO;
    }
}