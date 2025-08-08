package com.opencsms.web.controller;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.session.MeterValue;
import com.opencsms.service.session.ChargingSessionService;
import com.opencsms.service.session.SessionStatistics;
import com.opencsms.web.dto.session.ChargingSessionDto;
import com.opencsms.web.dto.session.MeterValueDto;
import com.opencsms.web.dto.session.SessionStatusHistoryDto;
import com.opencsms.web.mapper.session.ChargingSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for charging session management.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChargingSessionController {

    private final ChargingSessionService sessionService;
    private final ChargingSessionMapper sessionMapper;

    /**
     * Get all sessions with pagination
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<Page<ChargingSessionDto>> getAllSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ChargingSession> sessions = sessionService.getAllSessions(pageable);
        Page<ChargingSessionDto> sessionDtos = sessions.map(sessionMapper::toDto);
        
        return ResponseEntity.ok(sessionDtos);
    }

    /**
     * Get session by ID
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<ChargingSessionDto> getSessionById(@PathVariable UUID sessionId) {
        ChargingSession session = sessionService.getSessionById(sessionId);
        ChargingSessionDto sessionDto = sessionMapper.toDetailedDto(session);
        
        return ResponseEntity.ok(sessionDto);
    }

    /**
     * Get session by UUID
     */
    @GetMapping("/uuid/{sessionUuid}")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<ChargingSessionDto> getSessionByUuid(@PathVariable UUID sessionUuid) {
        ChargingSession session = sessionService.getSessionByUuid(sessionUuid);
        ChargingSessionDto sessionDto = sessionMapper.toDetailedDto(session);
        
        return ResponseEntity.ok(sessionDto);
    }

    /**
     * Get active sessions
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<List<ChargingSessionDto>> getActiveSessions() {
        List<ChargingSession> sessions = sessionService.getActiveSessions();
        List<ChargingSessionDto> sessionDtos = sessions.stream()
            .map(sessionMapper::toSummaryDto)
            .toList();
        
        return ResponseEntity.ok(sessionDtos);
    }

    /**
     * Get sessions by user
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<Page<ChargingSessionDto>> getSessionsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ChargingSession> sessions = sessionService.getSessionsByUser(userId, pageable);
        Page<ChargingSessionDto> sessionDtos = sessions.map(sessionMapper::toSummaryDto);
        
        return ResponseEntity.ok(sessionDtos);
    }

    /**
     * Get sessions by station
     */
    @GetMapping("/station/{stationId}")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<Page<ChargingSessionDto>> getSessionsByStation(
            @PathVariable UUID stationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ChargingSession> sessions = sessionService.getSessionsByStation(stationId, pageable);
        Page<ChargingSessionDto> sessionDtos = sessions.map(sessionMapper::toSummaryDto);
        
        return ResponseEntity.ok(sessionDtos);
    }

    /**
     * Create a new charging session
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SESSION:CREATE')")
    public ResponseEntity<ChargingSessionDto> createSession(
            @Valid @RequestBody ChargingSessionDto.CreateSessionRequest request) {
        
        ChargingSession session = sessionMapper.fromCreateRequest(request);
        ChargingSession createdSession = sessionService.createSession(session);
        ChargingSessionDto responseDto = sessionMapper.toDto(createdSession);
        
        log.info("Created charging session {} for connector {}", 
                createdSession.getId(), request.getConnectorId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    /**
     * Start a charging session
     */
    @PostMapping("/{sessionId}/start")
    @PreAuthorize("hasAuthority('SESSION:CONTROL')")
    public ResponseEntity<ChargingSessionDto> startSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChargingSessionDto.StartSessionRequest request) {
        
        ChargingSession startedSession = sessionService.startSession(
            sessionId, 
            request.getIdTag(), 
            request.getTransactionId(), 
            request.getMeterStart()
        );
        
        ChargingSessionDto responseDto = sessionMapper.toDto(startedSession);
        
        log.info("Started charging session {} with transaction {}", sessionId, request.getTransactionId());
        
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Stop a charging session
     */
    @PostMapping("/{sessionId}/stop")
    @PreAuthorize("hasAuthority('SESSION:CONTROL')")
    public ResponseEntity<ChargingSessionDto> stopSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChargingSessionDto.StopSessionRequest request) {
        
        ChargingSession stoppedSession = sessionService.stopSession(
            sessionId, 
            request.getMeterStop(), 
            request.getStopReason()
        );
        
        ChargingSessionDto responseDto = sessionMapper.toDto(stoppedSession);
        
        log.info("Stopped charging session {} - Energy: {} kWh, Cost: {} {}", 
                sessionId, stoppedSession.getEnergyDeliveredKwh(), 
                stoppedSession.getTotalCost(), stoppedSession.getCurrency());
        
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Suspend a charging session
     */
    @PostMapping("/{sessionId}/suspend")
    @PreAuthorize("hasAuthority('SESSION:CONTROL')")
    public ResponseEntity<ChargingSessionDto> suspendSession(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "false") boolean suspendedByEv) {
        
        ChargingSession suspendedSession = sessionService.suspendSession(sessionId, suspendedByEv);
        ChargingSessionDto responseDto = sessionMapper.toDto(suspendedSession);
        
        log.info("Suspended charging session {} - {}", sessionId, suspendedByEv ? "EV" : "EVSE");
        
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Resume a charging session
     */
    @PostMapping("/{sessionId}/resume")
    @PreAuthorize("hasAuthority('SESSION:CONTROL')")
    public ResponseEntity<ChargingSessionDto> resumeSession(@PathVariable UUID sessionId) {
        
        ChargingSession resumedSession = sessionService.resumeSession(sessionId);
        ChargingSessionDto responseDto = sessionMapper.toDto(resumedSession);
        
        log.info("Resumed charging session {}", sessionId);
        
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Remote stop session
     */
    @PostMapping("/{sessionId}/remote-stop")
    @PreAuthorize("hasAuthority('SESSION:REMOTE_CONTROL')")
    public ResponseEntity<Void> remoteStopSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) ChargingSessionDto.RemoteStopRequest request) {
        
        // This would typically send a RemoteStopTransaction command to the station
        // For now, we'll just log the request
        log.info("Remote stop requested for session {} - reason: {}", 
                sessionId, request != null ? request.getReason() : "Remote stop");
        
        return ResponseEntity.accepted().build();
    }

    /**
     * Add meter value to session
     */
    @PostMapping("/{sessionId}/meter-values")
    @PreAuthorize("hasAuthority('SESSION:UPDATE')")
    public ResponseEntity<Void> addMeterValue(
            @PathVariable UUID sessionId,
            @Valid @RequestBody MeterValueDto.AddMeterValueRequest request) {
        
        MeterValue meterValue = sessionMapper.fromAddMeterValueRequest(request);
        sessionService.addMeterValue(sessionId, meterValue);
        
        log.debug("Added meter value {} to session {}", request.getMeasurand(), sessionId);
        
        return ResponseEntity.created(null).build();
    }

    /**
     * Get meter values for session
     */
    @GetMapping("/{sessionId}/meter-values")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<List<MeterValueDto>> getMeterValues(@PathVariable UUID sessionId) {
        List<MeterValue> meterValues = sessionService.getMeterValues(sessionId);
        List<MeterValueDto> meterValueDtos = meterValues.stream()
            .map(sessionMapper::toMeterValueDto)
            .toList();
        
        return ResponseEntity.ok(meterValueDtos);
    }

    /**
     * Get session statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('SESSION:READ')")
    public ResponseEntity<SessionStatistics> getSessionStatistics(
            @RequestParam(required = false) String period) {
        
        Instant fromDate = Instant.now().minus(30, ChronoUnit.DAYS); // Default 30 days
        
        if ("7d".equals(period)) {
            fromDate = Instant.now().minus(7, ChronoUnit.DAYS);
        } else if ("1d".equals(period)) {
            fromDate = Instant.now().minus(1, ChronoUnit.DAYS);
        } else if ("1y".equals(period)) {
            fromDate = Instant.now().minus(365, ChronoUnit.DAYS);
        }
        
        SessionStatistics statistics = sessionService.getSessionStatistics(fromDate);
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Delete session (admin only)
     */
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAuthority('SESSION:DELETE')")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        sessionService.deleteSession(sessionId);
        
        log.info("Deleted charging session {}", sessionId);
        
        return ResponseEntity.noContent().build();
    }
}