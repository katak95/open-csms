package com.opencsms.domain.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ChargingSession entities.
 */
@Repository
public interface ChargingSessionRepository extends JpaRepository<ChargingSession, UUID> {

    /**
     * Find sessions by tenant
     */
    List<ChargingSession> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Page<ChargingSession> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Find active sessions
     */
    @Query("SELECT s FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.status IN :activeStatuses")
    List<ChargingSession> findActiveSessionsByTenant(@Param("tenantId") String tenantId, 
                                                   @Param("activeStatuses") List<ChargingSession.SessionStatus> activeStatuses);

    /**
     * Find session by OCPP transaction ID
     */
    Optional<ChargingSession> findByTenantIdAndOcppTransactionId(String tenantId, Integer ocppTransactionId);

    /**
     * Find session by UUID
     */
    Optional<ChargingSession> findByTenantIdAndSessionUuid(String tenantId, UUID sessionUuid);

    /**
     * Find sessions by user
     */
    Page<ChargingSession> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, UUID userId, Pageable pageable);

    /**
     * Find sessions by station
     */
    Page<ChargingSession> findByTenantIdAndStationIdOrderByCreatedAtDesc(String tenantId, UUID stationId, Pageable pageable);

    /**
     * Find sessions by connector
     */
    List<ChargingSession> findByTenantIdAndConnectorIdOrderByCreatedAtDesc(String tenantId, UUID connectorId);

    /**
     * Find active session for connector
     */
    @Query("SELECT s FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.connectorId = :connectorId AND s.status IN :activeStatuses")
    Optional<ChargingSession> findActiveSessionByConnector(@Param("tenantId") String tenantId, 
                                                         @Param("connectorId") UUID connectorId,
                                                         @Param("activeStatuses") List<ChargingSession.SessionStatus> activeStatuses);

    /**
     * Find sessions by status
     */
    List<ChargingSession> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ChargingSession.SessionStatus status);

    /**
     * Find sessions within date range
     */
    @Query("SELECT s FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.startTime >= :startDate AND s.startTime <= :endDate ORDER BY s.startTime DESC")
    Page<ChargingSession> findByTenantIdAndDateRange(@Param("tenantId") String tenantId, 
                                                   @Param("startDate") Instant startDate, 
                                                   @Param("endDate") Instant endDate, 
                                                   Pageable pageable);

    /**
     * Count active sessions by tenant
     */
    @Query("SELECT COUNT(s) FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.status IN :activeStatuses")
    long countActiveSessionsByTenant(@Param("tenantId") String tenantId, 
                                   @Param("activeStatuses") List<ChargingSession.SessionStatus> activeStatuses);

    /**
     * Count sessions by status
     */
    long countByTenantIdAndStatus(String tenantId, ChargingSession.SessionStatus status);

    /**
     * Find sessions for billing
     */
    @Query("SELECT s FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.status = 'COMPLETED' AND s.totalCost IS NULL ORDER BY s.endTime DESC")
    List<ChargingSession> findUnbilledCompletedSessions(@Param("tenantId") String tenantId);

    /**
     * Find sessions with meter values needed
     */
    @Query("SELECT DISTINCT s FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.status IN :chargingStatuses AND s.lastModifiedAt <= :staleThreshold")
    List<ChargingSession> findStaleChargingSessions(@Param("tenantId") String tenantId,
                                                  @Param("chargingStatuses") List<ChargingSession.SessionStatus> chargingStatuses,
                                                  @Param("staleThreshold") Instant staleThreshold);

    /**
     * Find sessions by auth token
     */
    List<ChargingSession> findByTenantIdAndAuthTokenIdOrderByCreatedAtDesc(String tenantId, UUID authTokenId);

    /**
     * Find roaming sessions
     */
    List<ChargingSession> findByTenantIdAndRoamingSessionTrueOrderByCreatedAtDesc(String tenantId);

    /**
     * Find sessions by reservation
     */
    List<ChargingSession> findByTenantIdAndReservationId(String tenantId, UUID reservationId);

    /**
     * Statistics queries
     */
    @Query("SELECT COUNT(s), SUM(s.energyDeliveredKwh), AVG(s.durationMinutes), SUM(s.totalCost) " +
           "FROM ChargingSession s WHERE s.tenantId = :tenantId AND s.status = 'COMPLETED' AND s.startTime >= :fromDate")
    Object[] getSessionStatistics(@Param("tenantId") String tenantId, @Param("fromDate") Instant fromDate);
}