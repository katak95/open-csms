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
 * Repository for Connector operations.
 */
@Repository
public interface ConnectorRepository extends JpaRepository<Connector, UUID> {

    Optional<Connector> findByChargingStationIdAndConnectorId(UUID chargingStationId, Integer connectorId);
    
    @Query("SELECT c FROM Connector c WHERE c.chargingStation.stationId = :stationId AND " +
           "c.connectorId = :connectorId AND c.tenantId = :tenantId")
    Optional<Connector> findByStationIdAndConnectorId(@Param("stationId") String stationId, 
                                                     @Param("connectorId") Integer connectorId, 
                                                     @Param("tenantId") String tenantId);
    
    List<Connector> findByChargingStationId(UUID chargingStationId);
    
    @Query("SELECT c FROM Connector c WHERE c.chargingStation.stationId = :stationId AND c.tenantId = :tenantId")
    List<Connector> findByStationId(@Param("stationId") String stationId, @Param("tenantId") String tenantId);
    
    List<Connector> findByTenantIdAndStatus(String tenantId, Connector.ConnectorStatus status);
    
    List<Connector> findByTenantIdAndConnectorType(String tenantId, Connector.ConnectorType connectorType);
    
    List<Connector> findByTenantIdAndStandard(String tenantId, Connector.ConnectorStandard standard);
    
    List<Connector> findByTenantIdAndPowerType(String tenantId, Connector.PowerType powerType);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.status = 'AVAILABLE' AND c.maintenanceMode = false AND c.deleted = false")
    List<Connector> findAvailableConnectors(@Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.currentTransactionId IS NOT NULL")
    List<Connector> findActiveTransactions(@Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.reservationId IS NOT NULL AND c.reservationExpiresAt > :now")
    List<Connector> findActiveReservations(@Param("tenantId") String tenantId, @Param("now") Instant now);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.reservationId IS NOT NULL AND c.reservationExpiresAt <= :now")
    List<Connector> findExpiredReservations(@Param("tenantId") String tenantId, @Param("now") Instant now);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.maintenanceMode = true")
    List<Connector> findInMaintenanceMode(@Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.errorCode IS NOT NULL")
    List<Connector> findWithErrors(@Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.maxElectricPower >= :minPower AND c.status = 'AVAILABLE' AND " +
           "c.maintenanceMode = false AND c.deleted = false")
    List<Connector> findAvailableByMinPower(@Param("tenantId") String tenantId, 
                                          @Param("minPower") BigDecimal minPower);
    
    @Query("SELECT COUNT(c) FROM Connector c WHERE c.tenantId = :tenantId AND c.deleted = false")
    long countByTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(c) FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.status = :status AND c.deleted = false")
    long countByStatus(@Param("tenantId") String tenantId, 
                      @Param("status") Connector.ConnectorStatus status);
    
    @Query("SELECT COUNT(c) FROM Connector c WHERE c.tenantId = :tenantId AND " +
           "c.connectorType = :type AND c.deleted = false")
    long countByType(@Param("tenantId") String tenantId, 
                    @Param("type") Connector.ConnectorType type);
    
    @Query("SELECT SUM(c.totalEnergyDeliveredKwh) FROM Connector c WHERE " +
           "c.tenantId = :tenantId AND c.deleted = false")
    BigDecimal getTotalEnergyDelivered(@Param("tenantId") String tenantId);
    
    @Query("SELECT SUM(c.totalSessions) FROM Connector c WHERE " +
           "c.tenantId = :tenantId AND c.deleted = false")
    Long getTotalSessions(@Param("tenantId") String tenantId);
    
    @Query("SELECT SUM(c.totalRevenue) FROM Connector c WHERE " +
           "c.tenantId = :tenantId AND c.deleted = false")
    BigDecimal getTotalRevenue(@Param("tenantId") String tenantId);
    
    @Query("SELECT AVG(c.uptimePercentage) FROM Connector c WHERE " +
           "c.tenantId = :tenantId AND c.deleted = false")
    BigDecimal getAverageUptime(@Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.currentIdTag = :idTag AND " +
           "c.currentTransactionId IS NOT NULL AND c.tenantId = :tenantId")
    Optional<Connector> findActiveTransactionByIdTag(@Param("idTag") String idTag, 
                                                   @Param("tenantId") String tenantId);
    
    @Query("SELECT c FROM Connector c WHERE c.reservedIdTag = :idTag AND " +
           "c.reservationExpiresAt > :now AND c.tenantId = :tenantId")
    List<Connector> findReservationsByIdTag(@Param("idTag") String idTag, 
                                          @Param("now") Instant now,
                                          @Param("tenantId") String tenantId);
}