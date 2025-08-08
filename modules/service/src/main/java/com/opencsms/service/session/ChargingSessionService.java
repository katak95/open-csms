package com.opencsms.service.session;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.session.ChargingSessionRepository;
import com.opencsms.domain.session.MeterValue;
import com.opencsms.domain.session.MeterValueRepository;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.station.ConnectorRepository;
import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import com.opencsms.service.core.TenantContext;
import com.opencsms.service.user.AuthTokenService;
import com.opencsms.service.user.UserService;
import com.opencsms.service.station.StationService;
import com.opencsms.service.session.events.SessionStartedEvent;
import com.opencsms.service.session.events.SessionStoppedEvent;
import com.opencsms.service.session.events.SessionStatusChangedEvent;
import com.opencsms.service.session.exception.SessionNotFoundException;
import com.opencsms.service.session.exception.SessionValidationException;
import com.opencsms.service.session.exception.InvalidSessionStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing charging sessions with complete lifecycle and state machine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChargingSessionService {

    private final ChargingSessionRepository sessionRepository;
    private final MeterValueRepository meterValueRepository;
    private final ConnectorRepository connectorRepository;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final StationService stationService;
    private final TariffService tariffService;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<ChargingSession.SessionStatus> ACTIVE_STATUSES = Arrays.asList(
        ChargingSession.SessionStatus.CHARGING,
        ChargingSession.SessionStatus.SUSPENDED_EV,
        ChargingSession.SessionStatus.SUSPENDED_EVSE,
        ChargingSession.SessionStatus.STARTING,
        ChargingSession.SessionStatus.AUTHORIZING
    );

    /**
     * Create a new charging session
     */
    public ChargingSession createSession(ChargingSession session) {
        String tenantId = TenantContext.getCurrentTenantId();
        session.setTenantId(tenantId);
        
        validateSessionCreation(session);
        
        // Set initial status and timestamp
        session.setStatus(ChargingSession.SessionStatus.PENDING);
        session.addStatusHistory(ChargingSession.SessionStatus.PENDING, "Session created");
        
        ChargingSession savedSession = sessionRepository.save(session);
        log.info("Created charging session {} for connector {}", savedSession.getId(), savedSession.getConnectorId());
        
        return savedSession;
    }

    /**
     * Start a charging session with authorization
     */
    public ChargingSession startSession(UUID sessionId, String idTag, Integer transactionId, BigDecimal meterStart) {
        ChargingSession session = getSessionById(sessionId);
        
        if (!session.canTransitionTo(ChargingSession.SessionStatus.STARTING)) {
            throw new InvalidSessionStateException("Cannot start session in status: " + session.getStatus());
        }

        // Validate authorization
        AuthToken authToken = authTokenService.findValidTokenByValue(idTag)
            .orElseThrow(() -> new SessionValidationException("Invalid authorization token: " + idTag));

        // Update session with transaction details
        session.setOcppTransactionId(transactionId);
        session.setOcppIdTag(idTag);
        session.setAuthToken(authToken);
        session.setUserId(authToken.getUserId());
        session.setMeterStart(meterStart);
        session.setStartTime(Instant.now());
        session.setAuthorizationTime(Instant.now());

        // Apply tariff
        applyTariffToSession(session);

        // Transition to charging
        session.transitionTo(ChargingSession.SessionStatus.CHARGING, "Transaction started with ID: " + transactionId);
        
        ChargingSession savedSession = sessionRepository.save(session);
        
        // Update connector status
        updateConnectorStatus(session.getConnectorId(), "Occupied");
        
        // Publish event
        eventPublisher.publishEvent(new SessionStartedEvent(savedSession));
        
        log.info("Started charging session {} for user {} on connector {}", 
                savedSession.getId(), authToken.getUserId(), savedSession.getConnectorId());
        
        return savedSession;
    }

    /**
     * Stop a charging session
     */
    public ChargingSession stopSession(UUID sessionId, BigDecimal meterStop, ChargingSession.StopReason reason) {
        ChargingSession session = getSessionById(sessionId);
        
        if (!session.canBeStopped()) {
            throw new InvalidSessionStateException("Cannot stop session in status: " + session.getStatus());
        }

        // Update session with stop details
        session.setMeterStop(meterStop);
        session.setEndTime(Instant.now());
        session.setStopReason(reason);

        // Calculate session metrics
        session.calculateDuration();
        session.calculateEnergyDelivered();
        session.calculateCosts();

        // Transition to completed
        session.transitionTo(ChargingSession.SessionStatus.COMPLETED, "Session stopped: " + reason);
        
        ChargingSession savedSession = sessionRepository.save(session);
        
        // Update connector status
        updateConnectorStatus(session.getConnectorId(), "Available");
        
        // Publish event
        eventPublisher.publishEvent(new SessionStoppedEvent(savedSession));
        
        log.info("Stopped charging session {} - Energy: {} kWh, Duration: {} min, Cost: {} {}", 
                savedSession.getId(), savedSession.getEnergyDeliveredKwh(), 
                savedSession.getDurationMinutes(), savedSession.getTotalCost(), savedSession.getCurrency());
        
        return savedSession;
    }

    /**
     * Suspend a charging session
     */
    public ChargingSession suspendSession(UUID sessionId, boolean suspendedByEV) {
        ChargingSession session = getSessionById(sessionId);
        
        ChargingSession.SessionStatus newStatus = suspendedByEV ? 
            ChargingSession.SessionStatus.SUSPENDED_EV : ChargingSession.SessionStatus.SUSPENDED_EVSE;
        
        if (session.transitionTo(newStatus, "Session suspended")) {
            ChargingSession savedSession = sessionRepository.save(session);
            
            eventPublisher.publishEvent(new SessionStatusChangedEvent(savedSession, session.getStatus(), newStatus));
            
            log.info("Suspended charging session {} - {}", sessionId, suspendedByEV ? "EV" : "EVSE");
            return savedSession;
        }
        
        throw new InvalidSessionStateException("Cannot suspend session in status: " + session.getStatus());
    }

    /**
     * Resume a charging session
     */
    public ChargingSession resumeSession(UUID sessionId) {
        ChargingSession session = getSessionById(sessionId);
        
        if (session.transitionTo(ChargingSession.SessionStatus.CHARGING, "Session resumed")) {
            ChargingSession savedSession = sessionRepository.save(session);
            
            eventPublisher.publishEvent(new SessionStatusChangedEvent(savedSession, session.getStatus(), 
                                                                    ChargingSession.SessionStatus.CHARGING));
            
            log.info("Resumed charging session {}", sessionId);
            return savedSession;
        }
        
        throw new InvalidSessionStateException("Cannot resume session in status: " + session.getStatus());
    }

    /**
     * Add meter value to session
     */
    public void addMeterValue(UUID sessionId, MeterValue meterValue) {
        ChargingSession session = getSessionById(sessionId);
        
        meterValue.setSessionId(sessionId);
        meterValue.setTimestamp(Instant.now());
        meterValue.processValue(); // Convert units
        
        meterValueRepository.save(meterValue);
        
        // Update session with latest values
        session.addMeterValue(meterValue);
        sessionRepository.save(session);
        
        log.debug("Added meter value {} to session {}: {} {} {}", 
                meterValue.getMeasurand(), sessionId, meterValue.getValue(), 
                meterValue.getUnit(), meterValue.getContext());
    }

    /**
     * Get session by ID
     */
    @Transactional(readOnly = true)
    public ChargingSession getSessionById(UUID sessionId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findById(sessionId)
            .filter(session -> tenantId.equals(session.getTenantId()))
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));
    }

    /**
     * Get session by UUID
     */
    @Transactional(readOnly = true)
    public ChargingSession getSessionByUuid(UUID sessionUuid) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findByTenantIdAndSessionUuid(tenantId, sessionUuid)
            .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionUuid));
    }

    /**
     * Get session by OCPP transaction ID
     */
    @Transactional(readOnly = true)
    public ChargingSession getSessionByTransactionId(Integer transactionId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findByTenantIdAndOcppTransactionId(tenantId, transactionId)
            .orElseThrow(() -> new SessionNotFoundException("Session not found for transaction: " + transactionId));
    }

    /**
     * Get all sessions for tenant
     */
    @Transactional(readOnly = true)
    public Page<ChargingSession> getAllSessions(Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    /**
     * Get active sessions
     */
    @Transactional(readOnly = true)
    public List<ChargingSession> getActiveSessions() {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findActiveSessionsByTenant(tenantId, ACTIVE_STATUSES);
    }

    /**
     * Get sessions by user
     */
    @Transactional(readOnly = true)
    public Page<ChargingSession> getSessionsByUser(UUID userId, Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, pageable);
    }

    /**
     * Get sessions by station
     */
    @Transactional(readOnly = true)
    public Page<ChargingSession> getSessionsByStation(UUID stationId, Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findByTenantIdAndStationIdOrderByCreatedAtDesc(tenantId, stationId, pageable);
    }

    /**
     * Get active session for connector
     */
    @Transactional(readOnly = true)
    public Optional<ChargingSession> getActiveSessionByConnector(UUID connectorId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return sessionRepository.findActiveSessionByConnector(tenantId, connectorId, ACTIVE_STATUSES);
    }

    /**
     * Get meter values for session
     */
    @Transactional(readOnly = true)
    public List<MeterValue> getMeterValues(UUID sessionId) {
        getSessionById(sessionId); // Validate session exists and tenant
        return meterValueRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Get session statistics
     */
    @Transactional(readOnly = true)
    public SessionStatistics getSessionStatistics(Instant fromDate) {
        String tenantId = TenantContext.getCurrentTenantId();
        Object[] stats = sessionRepository.getSessionStatistics(tenantId, fromDate);
        
        if (stats.length >= 4) {
            return SessionStatistics.builder()
                .totalSessions(((Number) stats[0]).longValue())
                .totalEnergyDelivered((BigDecimal) stats[1])
                .averageDurationMinutes(((Number) stats[2]).intValue())
                .totalRevenue((BigDecimal) stats[3])
                .build();
        }
        
        return SessionStatistics.builder().build();
    }

    /**
     * Delete session (admin only)
     */
    public void deleteSession(UUID sessionId) {
        ChargingSession session = getSessionById(sessionId);
        
        if (session.isActive()) {
            throw new InvalidSessionStateException("Cannot delete active session");
        }
        
        sessionRepository.delete(session);
        log.info("Deleted charging session {}", sessionId);
    }

    private void validateSessionCreation(ChargingSession session) {
        // Validate connector exists and is available
        String tenantId = TenantContext.getCurrentTenantId();
        Connector connector = connectorRepository.findById(session.getConnectorId())
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(() -> new SessionValidationException("Connector not found: " + session.getConnectorId()));

        // Check if connector already has active session
        Optional<ChargingSession> activeSession = getActiveSessionByConnector(session.getConnectorId());
        if (activeSession.isPresent()) {
            throw new SessionValidationException("Connector already has active session: " + activeSession.get().getId());
        }

        session.setStationId(connector.getStationId());
        session.setConnectorNumber(connector.getConnectorNumber());
    }

    private void applyTariffToSession(ChargingSession session) {
        try {
            Tariff tariff = tariffService.getDefaultTariff();
            session.setTariffId(tariff.getId());
            session.setCurrency(tariff.getCurrency());
            session.setPricePerKwh(tariff.getPricePerKwh());
            session.setPricePerMinute(tariff.getPricePerMinute());
            session.setServiceFee(tariff.getServiceFee());
        } catch (Exception e) {
            log.warn("Could not apply tariff to session {}: {}", session.getId(), e.getMessage());
            // Use default pricing
            session.setCurrency("EUR");
            session.setPricePerKwh(BigDecimal.valueOf(0.30));
            session.setPricePerMinute(BigDecimal.valueOf(0.02));
            session.setServiceFee(BigDecimal.ZERO);
        }
    }

    private void updateConnectorStatus(UUID connectorId, String status) {
        try {
            // Update connector status via StationService
            stationService.updateConnectorStatus(connectorId, status);
        } catch (Exception e) {
            log.warn("Could not update connector {} status to {}: {}", connectorId, status, e.getMessage());
        }
    }
}