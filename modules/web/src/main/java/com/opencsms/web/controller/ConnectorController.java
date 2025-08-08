package com.opencsms.web.controller;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.station.ConnectorRepository;
import com.opencsms.web.dto.ConnectorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for connector management.
 */
@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ConnectorController {

    private final ConnectorRepository connectorRepository;

    /**
     * Get all connectors for a charging station.
     */
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<ConnectorDto>> getConnectorsByStation(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors for station {} for tenant: {}", stationId, tenantId);
        
        List<Connector> connectors = connectorRepository.findByStationId(stationId, tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific connector by station ID and connector ID.
     */
    @GetMapping("/station/{stationId}/connector/{connectorId}")
    public ResponseEntity<ConnectorDto> getConnector(
            @PathVariable @NotBlank String stationId, 
            @PathVariable Integer connectorId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connector {} for station {} for tenant: {}", connectorId, stationId, tenantId);
        
        return connectorRepository.findByStationIdAndConnectorId(stationId, connectorId, tenantId)
            .map(ConnectorDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all available connectors.
     */
    @GetMapping("/available")
    public ResponseEntity<List<ConnectorDto>> getAvailableConnectors() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting available connectors for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findAvailableConnectors(tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors by status.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ConnectorDto>> getConnectorsByStatus(
            @PathVariable Connector.ConnectorStatus status) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors with status {} for tenant: {}", status, tenantId);
        
        List<Connector> connectors = connectorRepository.findByTenantIdAndStatus(tenantId, status);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors by type.
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<ConnectorDto>> getConnectorsByType(
            @PathVariable Connector.ConnectorType type) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors with type {} for tenant: {}", type, tenantId);
        
        List<Connector> connectors = connectorRepository.findByTenantIdAndConnectorType(tenantId, type);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors by power type.
     */
    @GetMapping("/power-type/{powerType}")
    public ResponseEntity<List<ConnectorDto>> getConnectorsByPowerType(
            @PathVariable Connector.PowerType powerType) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors with power type {} for tenant: {}", powerType, tenantId);
        
        List<Connector> connectors = connectorRepository.findByTenantIdAndPowerType(tenantId, powerType);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors with minimum power capacity.
     */
    @GetMapping("/min-power/{minPower}")
    public ResponseEntity<List<ConnectorDto>> getConnectorsByMinPower(
            @PathVariable BigDecimal minPower) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors with min power {} kW for tenant: {}", minPower, tenantId);
        
        List<Connector> connectors = connectorRepository.findAvailableByMinPower(tenantId, minPower);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get active transactions.
     */
    @GetMapping("/active-transactions")
    public ResponseEntity<List<ConnectorDto>> getActiveTransactions() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting active transactions for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findActiveTransactions(tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get active reservations.
     */
    @GetMapping("/active-reservations")
    public ResponseEntity<List<ConnectorDto>> getActiveReservations() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting active reservations for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findActiveReservations(tenantId, Instant.now());
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get expired reservations.
     */
    @GetMapping("/expired-reservations")
    public ResponseEntity<List<ConnectorDto>> getExpiredReservations() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting expired reservations for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findExpiredReservations(tenantId, Instant.now());
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors in maintenance mode.
     */
    @GetMapping("/maintenance")
    public ResponseEntity<List<ConnectorDto>> getConnectorsInMaintenance() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors in maintenance for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findInMaintenanceMode(tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connectors with errors.
     */
    @GetMapping("/errors")
    public ResponseEntity<List<ConnectorDto>> getConnectorsWithErrors() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connectors with errors for tenant: {}", tenantId);
        
        List<Connector> connectors = connectorRepository.findWithErrors(tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get connector statistics.
     */
    @GetMapping("/statistics")
    public ResponseEntity<ConnectorStatistics> getConnectorStatistics() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting connector statistics for tenant: {}", tenantId);
        
        try {
            long totalConnectors = connectorRepository.countByTenant(tenantId);
            long availableConnectors = connectorRepository.countByStatus(tenantId, Connector.ConnectorStatus.AVAILABLE);
            long occupiedConnectors = connectorRepository.countByStatus(tenantId, Connector.ConnectorStatus.OCCUPIED);
            long faultedConnectors = connectorRepository.countByStatus(tenantId, Connector.ConnectorStatus.FAULTED);
            long unavailableConnectors = connectorRepository.countByStatus(tenantId, Connector.ConnectorStatus.UNAVAILABLE);
            
            BigDecimal totalEnergy = connectorRepository.getTotalEnergyDelivered(tenantId);
            Long totalSessions = connectorRepository.getTotalSessions(tenantId);
            BigDecimal totalRevenue = connectorRepository.getTotalRevenue(tenantId);
            BigDecimal averageUptime = connectorRepository.getAverageUptime(tenantId);
            
            ConnectorStatistics stats = ConnectorStatistics.builder()
                .totalConnectors(totalConnectors)
                .availableConnectors(availableConnectors)
                .occupiedConnectors(occupiedConnectors)
                .faultedConnectors(faultedConnectors)
                .unavailableConnectors(unavailableConnectors)
                .totalEnergyDelivered(totalEnergy)
                .totalSessions(totalSessions)
                .totalRevenue(totalRevenue)
                .averageUptime(averageUptime)
                .build();
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting connector statistics for tenant {}", tenantId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Find active transaction by ID tag.
     */
    @GetMapping("/active-transaction/{idTag}")
    public ResponseEntity<ConnectorDto> getActiveTransactionByIdTag(@PathVariable @NotBlank String idTag) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting active transaction for ID tag {} for tenant: {}", idTag, tenantId);
        
        return connectorRepository.findActiveTransactionByIdTag(idTag, tenantId)
            .map(ConnectorDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Find reservations by ID tag.
     */
    @GetMapping("/reservations/{idTag}")
    public ResponseEntity<List<ConnectorDto>> getReservationsByIdTag(@PathVariable @NotBlank String idTag) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting reservations for ID tag {} for tenant: {}", idTag, tenantId);
        
        List<Connector> connectors = connectorRepository.findReservationsByIdTag(idTag, Instant.now(), tenantId);
        List<ConnectorDto> dtos = connectors.stream()
            .map(ConnectorDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Connector statistics DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectorStatistics {
        private long totalConnectors;
        private long availableConnectors;
        private long occupiedConnectors;
        private long faultedConnectors;
        private long unavailableConnectors;
        private BigDecimal totalEnergyDelivered;
        private Long totalSessions;
        private BigDecimal totalRevenue;
        private BigDecimal averageUptime;
    }
}