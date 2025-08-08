package com.opencsms.service.ocpp;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.user.AuthToken;
import com.opencsms.service.core.TenantContext;
import com.opencsms.service.session.ChargingSessionService;
import com.opencsms.service.station.StationService;
import com.opencsms.service.user.AuthTokenService;
import com.opencsms.service.ocpp.model.ParsedOcppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling OCPP StartTransaction messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StartTransactionService {

    private final ChargingSessionService sessionService;
    private final AuthTokenService authTokenService;
    private final StationService stationService;

    /**
     * Process OCPP 1.6 StartTransaction message
     */
    public Map<String, Object> processStartTransaction16(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            
            // Extract parameters
            Integer connectorId = (Integer) payload.get("connectorId");
            String idTag = (String) payload.get("idTag");
            Integer meterStart = (Integer) payload.get("meterStart");
            String timestamp = (String) payload.get("timestamp");

            log.info("Processing StartTransaction for station {} connector {} with idTag {}", 
                    stationSerial, connectorId, idTag);

            // Find connector
            Connector connector = stationService.getConnectorByStationAndNumber(stationSerial, connectorId);
            
            // Check if connector is available
            Optional<ChargingSession> existingSession = sessionService.getActiveSessionByConnector(connector.getId());
            if (existingSession.isPresent()) {
                log.warn("Connector {} already has active session", connector.getId());
                return createStartTransactionResponse("Rejected", "ConnectorOccupied");
            }

            // Validate authorization token
            Optional<AuthToken> authToken = authTokenService.findValidTokenByValue(idTag);
            if (authToken.isEmpty()) {
                log.warn("Invalid idTag for StartTransaction: {}", idTag);
                return createStartTransactionResponse("Rejected", "InvalidToken");
            }

            // Check if user is blocked
            if (authToken.get().getStatus() != AuthToken.TokenStatus.ACTIVE) {
                log.warn("Blocked idTag for StartTransaction: {}", idTag);
                return createStartTransactionResponse("Rejected", "Blocked");
            }

            // Generate transaction ID
            Integer transactionId = generateTransactionId();

            // Create charging session
            ChargingSession session = ChargingSession.builder()
                .sessionUuid(UUID.randomUUID())
                .connectorId(connector.getId())
                .connectorNumber(connectorId)
                .stationId(connector.getStationId())
                .stationSerial(stationSerial)
                .authToken(authToken.get())
                .userId(authToken.get().getUserId())
                .ocppTransactionId(transactionId)
                .ocppIdTag(idTag)
                .meterStart(BigDecimal.valueOf(meterStart))
                .status(ChargingSession.SessionStatus.STARTING)
                .build();

            // Start the session
            ChargingSession startedSession = sessionService.startSession(
                sessionService.createSession(session).getId(), 
                idTag, 
                transactionId, 
                BigDecimal.valueOf(meterStart)
            );

            log.info("Started transaction {} for session {} on connector {}", 
                    transactionId, startedSession.getId(), connector.getId());

            return createStartTransactionResponse("Accepted", null, transactionId);

        } catch (Exception e) {
            log.error("Error processing StartTransaction for station {}: {}", stationSerial, e.getMessage(), e);
            return createStartTransactionResponse("Rejected", "InternalError");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Process OCPP 2.0.1 TransactionEvent with eventType Started
     */
    public Map<String, Object> processTransactionEventStarted(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            Map<String, Object> transactionInfo = (Map<String, Object>) payload.get("transactionInfo");
            Map<String, Object> evse = (Map<String, Object>) payload.get("evse");
            Map<String, Object> idToken = (Map<String, Object>) payload.get("idToken");

            // Extract parameters
            String transactionId = (String) transactionInfo.get("transactionId");
            Integer evseId = (Integer) evse.get("id");
            Integer connectorId = (Integer) ((Map<String, Object>) evse.get("connectorId")).get("id");
            String idTokenValue = (String) idToken.get("idToken");
            
            // Get meter value if present
            BigDecimal meterValue = BigDecimal.ZERO;
            if (payload.containsKey("meterValue")) {
                // Extract meter value from OCPP 2.0.1 format
                // This is more complex in 2.0.1 due to structured meter values
                meterValue = extractMeterValueFromOcpp201(payload);
            }

            log.info("Processing TransactionEvent Started for station {} EVSE {} connector {} with token {}", 
                    stationSerial, evseId, connectorId, idTokenValue);

            // Find connector
            Connector connector = stationService.getConnectorByStationAndNumber(stationSerial, connectorId);
            
            // Validate and start session similar to OCPP 1.6
            Optional<AuthToken> authToken = authTokenService.findValidTokenByValue(idTokenValue);
            if (authToken.isEmpty()) {
                return createTransactionEventResponse("Rejected", "InvalidToken");
            }

            // Create and start session
            ChargingSession session = ChargingSession.builder()
                .sessionUuid(UUID.randomUUID())
                .connectorId(connector.getId())
                .connectorNumber(connectorId)
                .stationId(connector.getStationId())
                .stationSerial(stationSerial)
                .authToken(authToken.get())
                .userId(authToken.get().getUserId())
                .ocppIdTag(idTokenValue)
                .meterStart(meterValue)
                .status(ChargingSession.SessionStatus.STARTING)
                .build();

            ChargingSession startedSession = sessionService.startSession(
                sessionService.createSession(session).getId(), 
                idTokenValue, 
                transactionId.hashCode(), // Convert string ID to integer for compatibility
                meterValue
            );

            log.info("Started OCPP 2.0.1 transaction {} for session {}", transactionId, startedSession.getId());

            return createTransactionEventResponse("Accepted", null);

        } catch (Exception e) {
            log.error("Error processing TransactionEvent Started for station {}: {}", stationSerial, e.getMessage(), e);
            return createTransactionEventResponse("Rejected", "InternalError");
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> createStartTransactionResponse(String status, String statusReason) {
        return createStartTransactionResponse(status, statusReason, null);
    }

    private Map<String, Object> createStartTransactionResponse(String status, String statusReason, Integer transactionId) {
        Map<String, Object> response = Map.of(
            "idTagInfo", Map.of(
                "status", status
            )
        );

        if (transactionId != null) {
            response = Map.of(
                "idTagInfo", Map.of("status", status),
                "transactionId", transactionId
            );
        }

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

    private Integer generateTransactionId() {
        // Simple transaction ID generation - in production use more robust approach
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    private BigDecimal extractMeterValueFromOcpp201(Map<String, Object> payload) {
        // OCPP 2.0.1 has complex meter value structure
        // For now, return zero - would need full implementation
        return BigDecimal.ZERO;
    }
}