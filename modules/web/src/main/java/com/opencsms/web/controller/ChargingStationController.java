package com.opencsms.web.controller;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.station.ChargingStation;
import com.opencsms.service.station.StationService;
import com.opencsms.service.station.StationStatistics;
import com.opencsms.web.dto.ChargingStationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for charging station management.
 */
@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ChargingStationController {

    private final StationService stationService;

    /**
     * Get all active charging stations for current tenant.
     */
    @GetMapping
    public ResponseEntity<List<ChargingStationDto>> getAllStations() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting all stations for tenant: {}", tenantId);
        
        List<ChargingStation> stations = stationService.findAllActive();
        List<ChargingStationDto> dtos = stations.stream()
            .map(ChargingStationDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Search charging stations by various criteria.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChargingStationDto>> searchStations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ChargingStation.StationStatus status,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false, defaultValue = "10.0") double radiusKm) {
        
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Searching stations for tenant: {} with query: {}, status: {}", tenantId, query, status);
        
        List<ChargingStation> stations;
        
        if (latitude != null && longitude != null) {
            // Search by location
            stations = stationService.findNearbyStations(latitude, longitude, radiusKm);
        } else if (query != null && !query.trim().isEmpty()) {
            // Search by text
            stations = stationService.searchStations(query.trim());
        } else if (status != null) {
            // Search by status
            stations = stationService.findByStatus(status);
        } else {
            // Default: return all active stations
            stations = stationService.findAllActive();
        }
        
        List<ChargingStationDto> dtos = stations.stream()
            .map(ChargingStationDto::fromEntity)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific charging station by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChargingStationDto> getStationById(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting station {} for tenant: {}", id, tenantId);
        
        return stationService.findById(id)
            .map(ChargingStationDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a charging station by station ID.
     */
    @GetMapping("/by-station-id/{stationId}")
    public ResponseEntity<ChargingStationDto> getStationByStationId(
            @PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting station with stationId {} for tenant: {}", stationId, tenantId);
        
        return stationService.findByStationId(stationId)
            .map(ChargingStationDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new charging station.
     */
    @PostMapping
    public ResponseEntity<ChargingStationDto> createStation(@Valid @RequestBody ChargingStationDto stationDto) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creating station {} for tenant: {}", stationDto.getStationId(), tenantId);
        
        try {
            ChargingStation station = stationDto.toEntity();
            ChargingStation createdStation = stationService.createStation(station);
            ChargingStationDto responseDto = ChargingStationDto.fromEntity(createdStation);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid station data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating station", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing charging station.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ChargingStationDto> updateStation(
            @PathVariable UUID id, 
            @Valid @RequestBody ChargingStationDto stationDto) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Updating station {} for tenant: {}", id, tenantId);
        
        try {
            ChargingStation updatedStation = stationService.updateStation(id, stationDto.toEntity());
            ChargingStationDto responseDto = ChargingStationDto.fromEntity(updatedStation);
            
            return ResponseEntity.ok(responseDto);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid station data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a charging station.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Deleting station {} for tenant: {}", id, tenantId);
        
        try {
            stationService.deleteStation(id);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot delete station: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error deleting station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Activate a charging station.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateStation(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Activating station {} for tenant: {}", id, tenantId);
        
        try {
            stationService.activateStation(id);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error activating station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deactivate a charging station.
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateStation(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Deactivating station {} for tenant: {}", id, tenantId);
        
        try {
            stationService.deactivateStation(id);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deactivating station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enter maintenance mode for a station.
     */
    @PostMapping("/{id}/maintenance/start")
    public ResponseEntity<Void> startMaintenance(
            @PathVariable UUID id, 
            @RequestParam(required = false) String reason) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Starting maintenance for station {} for tenant: {} - reason: {}", id, tenantId, reason);
        
        try {
            stationService.enterMaintenanceMode(id, reason != null ? reason : "Scheduled maintenance");
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error starting maintenance for station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exit maintenance mode for a station.
     */
    @PostMapping("/{id}/maintenance/end")
    public ResponseEntity<Void> endMaintenance(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Ending maintenance for station {} for tenant: {}", id, tenantId);
        
        try {
            stationService.exitMaintenanceMode(id);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error ending maintenance for station {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get station statistics.
     */
    @GetMapping("/statistics")
    public ResponseEntity<StationStatistics> getStationStatistics() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting station statistics for tenant: {}", tenantId);
        
        try {
            StationStatistics statistics = stationService.getStationStatistics();
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("Error getting station statistics for tenant {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Find stations with expired heartbeat.
     */
    @GetMapping("/expired-heartbeat")
    public ResponseEntity<List<ChargingStationDto>> getStationsWithExpiredHeartbeat() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting stations with expired heartbeat for tenant: {}", tenantId);
        
        try {
            List<ChargingStation> stations = stationService.findStationsWithExpiredHeartbeat();
            List<ChargingStationDto> dtos = stations.stream()
                .map(ChargingStationDto::fromEntity)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
            
        } catch (Exception e) {
            log.error("Error getting stations with expired heartbeat for tenant {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Record heartbeat for a station (usually called by OCPP).
     */
    @PostMapping("/by-station-id/{stationId}/heartbeat")
    public ResponseEntity<Void> recordHeartbeat(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("Recording heartbeat for station {} for tenant: {}", stationId, tenantId);
        
        try {
            stationService.updateHeartbeat(stationId);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error recording heartbeat for station {}", stationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Record boot notification for a station (usually called by OCPP).
     */
    @PostMapping("/by-station-id/{stationId}/boot-notification")
    public ResponseEntity<Void> recordBootNotification(@PathVariable @NotBlank String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Recording boot notification for station {} for tenant: {}", stationId, tenantId);
        
        try {
            stationService.recordBootNotification(stationId);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.warn("Station not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error recording boot notification for station {}", stationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}