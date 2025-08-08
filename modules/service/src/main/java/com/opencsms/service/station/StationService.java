package com.opencsms.service.station;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.station.ChargingStation;
import com.opencsms.domain.station.ChargingStationRepository;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.station.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for charging station operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StationService {

    private final ChargingStationRepository stationRepository;
    private final ConnectorRepository connectorRepository;
    private final StationValidationService validationService;
    private final StationEventService eventService;

    /**
     * Find station by ID within current tenant.
     */
    @Cacheable(value = "stations", key = "#id + '-' + T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public Optional<ChargingStation> findById(UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return stationRepository.findById(id)
            .filter(station -> station.getTenantId().equals(tenantId));
    }

    /**
     * Find station by station ID within current tenant.
     */
    @Cacheable(value = "stations", key = "#stationId + '-' + T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public Optional<ChargingStation> findByStationId(String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        return stationRepository.findByStationIdAndTenantId(stationId, tenantId);
    }

    /**
     * Find all active stations for current tenant.
     */
    @Cacheable(value = "stations-list", key = "T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public List<ChargingStation> findAllActive() {
        String tenantId = TenantContext.getCurrentTenant();
        return stationRepository.findByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
    }

    /**
     * Find stations by status.
     */
    public List<ChargingStation> findByStatus(ChargingStation.StationStatus status) {
        String tenantId = TenantContext.getCurrentTenant();
        return stationRepository.findByTenantIdAndStatus(tenantId, status);
    }

    /**
     * Find nearby stations within radius (in kilometers).
     */
    public List<ChargingStation> findNearbyStations(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        String tenantId = TenantContext.getCurrentTenant();
        // Convert radius to approximate degrees (rough calculation)
        BigDecimal radiusSquared = BigDecimal.valueOf((radiusKm / 111.0) * (radiusKm / 111.0));
        return stationRepository.findNearbyStations(tenantId, latitude, longitude, radiusSquared);
    }

    /**
     * Search stations by name, ID, or address.
     */
    public List<ChargingStation> searchStations(String searchTerm) {
        String tenantId = TenantContext.getCurrentTenant();
        return stationRepository.searchStations(tenantId, searchTerm);
    }

    /**
     * Create a new charging station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public ChargingStation createStation(ChargingStation station) {
        String tenantId = TenantContext.getCurrentTenant();
        
        // Validate station data
        validationService.validateStation(station);
        
        // Check for duplicate station ID
        if (stationRepository.existsByStationIdAndTenantId(station.getStationId(), tenantId)) {
            throw new IllegalArgumentException("Station ID already exists: " + station.getStationId());
        }

        // Set tenant ID
        station.setTenantId(tenantId);
        
        // Save station
        ChargingStation savedStation = stationRepository.save(station);
        
        log.info("Created charging station: {} for tenant: {}", savedStation.getStationId(), tenantId);
        
        // Publish event
        eventService.publishStationCreated(savedStation);
        
        return savedStation;
    }

    /**
     * Update an existing charging station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public ChargingStation updateStation(UUID id, ChargingStation updatedStation) {
        String tenantId = TenantContext.getCurrentTenant();
        
        ChargingStation existingStation = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        // Validate updated data
        validationService.validateStation(updatedStation);
        
        // Prevent station ID changes
        if (!existingStation.getStationId().equals(updatedStation.getStationId())) {
            throw new IllegalArgumentException("Station ID cannot be changed");
        }
        
        // Update fields
        updateStationFields(existingStation, updatedStation);
        
        ChargingStation savedStation = stationRepository.save(existingStation);
        
        log.info("Updated charging station: {} for tenant: {}", savedStation.getStationId(), tenantId);
        
        // Publish event
        eventService.publishStationUpdated(savedStation);
        
        return savedStation;
    }

    /**
     * Soft delete a charging station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public void deleteStation(UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        
        ChargingStation station = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        // Check if station has active transactions
        List<Connector> activeTransactions = connectorRepository.findActiveTransactions(tenantId);
        boolean hasActiveTransactions = activeTransactions.stream()
            .anyMatch(c -> c.getChargingStation().getId().equals(id));
        
        if (hasActiveTransactions) {
            throw new IllegalStateException("Cannot delete station with active transactions");
        }
        
        // Soft delete
        station.softDelete(TenantContext.getCurrentTenant());
        stationRepository.save(station);
        
        log.info("Deleted charging station: {} for tenant: {}", station.getStationId(), tenantId);
        
        // Publish event
        eventService.publishStationDeleted(station);
    }

    /**
     * Activate a charging station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public void activateStation(UUID id) {
        ChargingStation station = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        station.activate();
        stationRepository.save(station);
        
        log.info("Activated charging station: {}", station.getStationId());
        eventService.publishStationActivated(station);
    }

    /**
     * Deactivate a charging station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public void deactivateStation(UUID id) {
        ChargingStation station = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        station.deactivate();
        stationRepository.save(station);
        
        log.info("Deactivated charging station: {}", station.getStationId());
        eventService.publishStationDeactivated(station);
    }

    /**
     * Enter maintenance mode for a station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public void enterMaintenanceMode(UUID id, String reason) {
        ChargingStation station = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        station.enterMaintenanceMode(reason);
        stationRepository.save(station);
        
        log.info("Entered maintenance mode for station: {} - Reason: {}", 
                station.getStationId(), reason);
        eventService.publishStationMaintenanceStarted(station, reason);
    }

    /**
     * Exit maintenance mode for a station.
     */
    @Transactional
    @CacheEvict(value = {"stations", "stations-list"}, allEntries = true)
    public void exitMaintenanceMode(UUID id) {
        ChargingStation station = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + id));
        
        station.exitMaintenanceMode();
        stationRepository.save(station);
        
        log.info("Exited maintenance mode for station: {}", station.getStationId());
        eventService.publishStationMaintenanceEnded(station);
    }

    /**
     * Update station connection status (heartbeat).
     */
    @Transactional
    @CacheEvict(value = "stations", key = "#stationId + '-' + T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public void updateHeartbeat(String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        
        ChargingStation station = findByStationId(stationId)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));
        
        station.recordHeartbeat();
        stationRepository.save(station);
        
        log.debug("Updated heartbeat for station: {}", stationId);
    }

    /**
     * Record boot notification.
     */
    @Transactional
    @CacheEvict(value = "stations", key = "#stationId + '-' + T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public void recordBootNotification(String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        
        ChargingStation station = findByStationId(stationId)
            .orElseThrow(() -> new IllegalArgumentException("Station not found: " + stationId));
        
        station.recordBootNotification();
        stationRepository.save(station);
        
        log.info("Recorded boot notification for station: {}", stationId);
        eventService.publishStationConnected(station);
    }

    /**
     * Mark station as disconnected.
     */
    @Transactional
    @CacheEvict(value = "stations", key = "#stationId + '-' + T(com.opencsms.core.tenant.TenantContext).getCurrentTenant()")
    public void markDisconnected(String stationId) {
        String tenantId = TenantContext.getCurrentTenant();
        
        Optional<ChargingStation> stationOpt = findByStationId(stationId);
        if (stationOpt.isPresent()) {
            ChargingStation station = stationOpt.get();
            station.disconnect();
            stationRepository.save(station);
            
            log.warn("Marked station as disconnected: {}", stationId);
            eventService.publishStationDisconnected(station);
        }
    }

    /**
     * Get station statistics.
     */
    public StationStatistics getStationStatistics() {
        String tenantId = TenantContext.getCurrentTenant();
        
        return StationStatistics.builder()
            .totalStations(stationRepository.countActiveStations(tenantId))
            .onlineStations(stationRepository.countOnlineStations(tenantId))
            .availableStations(stationRepository.countByStatus(tenantId, ChargingStation.StationStatus.AVAILABLE))
            .occupiedStations(stationRepository.countByStatus(tenantId, ChargingStation.StationStatus.OCCUPIED))
            .faultedStations(stationRepository.countByStatus(tenantId, ChargingStation.StationStatus.FAULTED))
            .totalEnergyDelivered(stationRepository.getTotalEnergyDelivered(tenantId))
            .totalSessions(stationRepository.getTotalSessions(tenantId))
            .totalRevenue(stationRepository.getTotalRevenue(tenantId))
            .build();
    }

    /**
     * Find stations with expired heartbeat.
     */
    public List<ChargingStation> findStationsWithExpiredHeartbeat() {
        String tenantId = TenantContext.getCurrentTenant();
        Instant expiredBefore = Instant.now().minusSeconds(600); // 10 minutes
        return stationRepository.findStationsWithExpiredHeartbeat(tenantId, expiredBefore);
    }

    private void updateStationFields(ChargingStation existing, ChargingStation updated) {
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setPostalCode(updated.getPostalCode());
        existing.setCountry(updated.getCountry());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setAltitude(updated.getAltitude());
        existing.setNetworkOperator(updated.getNetworkOperator());
        existing.setOperatorName(updated.getOperatorName());
        existing.setNetworkId(updated.getNetworkId());
        existing.setMaxPowerKw(updated.getMaxPowerKw());
        existing.setHeartbeatInterval(updated.getHeartbeatInterval());
        existing.setMeterValuesSampleInterval(updated.getMeterValuesSampleInterval());
        existing.setClockAlignedDataInterval(updated.getClockAlignedDataInterval());
        existing.setConnectionTimeout(updated.getConnectionTimeout());
        existing.setLocalAuthorizeOffline(updated.isLocalAuthorizeOffline());
        existing.setLocalPreAuthorize(updated.isLocalPreAuthorize());
        existing.setAllowOfflineTxForUnknownId(updated.isAllowOfflineTxForUnknownId());
        existing.setAuthorizationCacheEnabled(updated.isAuthorizationCacheEnabled());
        existing.setSmartChargingEnabled(updated.isSmartChargingEnabled());
        
        // Update metadata if provided
        if (updated.getMetadata() != null && !updated.getMetadata().isEmpty()) {
            existing.getMetadata().putAll(updated.getMetadata());
        }
    }
}