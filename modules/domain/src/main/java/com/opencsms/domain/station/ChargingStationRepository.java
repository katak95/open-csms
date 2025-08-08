package com.opencsms.domain.station;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ChargingStation operations.
 */
@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {

    Optional<ChargingStation> findByStationIdAndTenantId(String stationId, String tenantId);
    
    List<ChargingStation> findByTenantIdAndActiveTrue(String tenantId);
    
    List<ChargingStation> findByTenantIdAndActiveTrueAndDeletedFalse(String tenantId);
    
    List<ChargingStation> findByTenantIdAndStatus(String tenantId, ChargingStation.StationStatus status);
    
    List<ChargingStation> findByTenantIdAndAvailability(String tenantId, ChargingStation.StationAvailability availability);
    
    List<ChargingStation> findByTenantIdAndConnectedTrue(String tenantId);
    
    List<ChargingStation> findByTenantIdAndConnectedFalse(String tenantId);
    
    List<ChargingStation> findByTenantIdAndMaintenanceModeTrue(String tenantId);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "(:lat - s.latitude) * (:lat - s.latitude) + (:lng - s.longitude) * (:lng - s.longitude) < :radiusSquared " +
           "AND s.active = true AND s.deleted = false")
    List<ChargingStation> findNearbyStations(@Param("tenantId") String tenantId, 
                                           @Param("lat") BigDecimal latitude, 
                                           @Param("lng") BigDecimal longitude, 
                                           @Param("radiusSquared") BigDecimal radiusSquared);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.lastHeartbeat < :before AND s.connected = true")
    List<ChargingStation> findStationsWithExpiredHeartbeat(@Param("tenantId") String tenantId, 
                                                         @Param("before") Instant before);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.networkOperator = :networkOperator AND s.active = true")
    List<ChargingStation> findByNetworkOperator(@Param("tenantId") String tenantId, 
                                              @Param("networkOperator") String networkOperator);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.vendor = :vendor AND s.active = true")
    List<ChargingStation> findByVendor(@Param("tenantId") String tenantId, 
                                     @Param("vendor") String vendor);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.ocppVersion = :version AND s.active = true")
    List<ChargingStation> findByOcppVersion(@Param("tenantId") String tenantId, 
                                          @Param("version") String version);
    
    @Query("SELECT COUNT(s) FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.active = true AND s.deleted = false")
    long countActiveStations(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(s) FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.connected = true AND s.active = true")
    long countOnlineStations(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(s) FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "s.status = :status AND s.active = true")
    long countByStatus(@Param("tenantId") String tenantId, 
                      @Param("status") ChargingStation.StationStatus status);
    
    @Query("SELECT SUM(s.totalEnergyDeliveredKwh) FROM ChargingStation s WHERE " +
           "s.tenantId = :tenantId AND s.active = true")
    BigDecimal getTotalEnergyDelivered(@Param("tenantId") String tenantId);
    
    @Query("SELECT SUM(s.totalSessions) FROM ChargingStation s WHERE " +
           "s.tenantId = :tenantId AND s.active = true")
    Long getTotalSessions(@Param("tenantId") String tenantId);
    
    @Query("SELECT SUM(s.totalRevenue) FROM ChargingStation s WHERE " +
           "s.tenantId = :tenantId AND s.active = true")
    BigDecimal getTotalRevenue(@Param("tenantId") String tenantId);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.stationId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.address) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "AND s.active = true AND s.deleted = false")
    List<ChargingStation> searchStations(@Param("tenantId") String tenantId, 
                                       @Param("search") String searchTerm);
    
    boolean existsByStationIdAndTenantId(String stationId, String tenantId);
    
    @Query("SELECT s FROM ChargingStation s WHERE s.owner.id = :userId AND s.tenantId = :tenantId")
    List<ChargingStation> findByOwner(@Param("userId") UUID userId, @Param("tenantId") String tenantId);
}