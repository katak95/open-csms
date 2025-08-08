package com.opencsms.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for MeterValue entities.
 */
@Repository
public interface MeterValueRepository extends JpaRepository<MeterValue, UUID> {

    /**
     * Find meter values by session
     */
    List<MeterValue> findBySessionIdOrderByTimestampAsc(UUID sessionId);

    /**
     * Find meter values by measurand
     */
    List<MeterValue> findBySessionIdAndMeasurandOrderByTimestampAsc(UUID sessionId, MeterValue.Measurand measurand);

    /**
     * Find latest meter value by measurand
     */
    @Query("SELECT m FROM MeterValue m WHERE m.sessionId = :sessionId AND m.measurand = :measurand ORDER BY m.timestamp DESC LIMIT 1")
    MeterValue findLatestBySessionAndMeasurand(@Param("sessionId") UUID sessionId, @Param("measurand") MeterValue.Measurand measurand);

    /**
     * Find meter values within time range
     */
    List<MeterValue> findBySessionIdAndTimestampBetweenOrderByTimestampAsc(UUID sessionId, Instant startTime, Instant endTime);

    /**
     * Count meter values by session
     */
    long countBySessionId(UUID sessionId);

    /**
     * Find energy meter values for billing
     */
    @Query("SELECT m FROM MeterValue m WHERE m.sessionId = :sessionId AND m.measurand = 'ENERGY_ACTIVE_IMPORT_REGISTER' ORDER BY m.timestamp ASC")
    List<MeterValue> findEnergyMeterValuesBySession(@Param("sessionId") UUID sessionId);

    /**
     * Get session power curve
     */
    @Query("SELECT m FROM MeterValue m WHERE m.sessionId = :sessionId AND m.measurand = 'POWER_ACTIVE_IMPORT' ORDER BY m.timestamp ASC")
    List<MeterValue> findPowerMeterValuesBySession(@Param("sessionId") UUID sessionId);

    /**
     * Delete old meter values
     */
    void deleteByTimestampBefore(Instant cutoffDate);
}