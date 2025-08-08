package com.opencsms.domain.tariff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Tariff entities.
 */
@Repository
public interface TariffRepository extends JpaRepository<Tariff, UUID> {

    /**
     * Find tariffs by tenant
     */
    List<Tariff> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find active tariffs
     */
    List<Tariff> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(String tenantId);

    /**
     * Find default tariff
     */
    Optional<Tariff> findByTenantIdAndDefaultTariffTrueAndActiveTrue(String tenantId);

    /**
     * Find tariff by code
     */
    Optional<Tariff> findByTenantIdAndCode(String tenantId, String code);

    /**
     * Find public tariffs
     */
    List<Tariff> findByTenantIdAndPublicTariffTrueAndActiveTrueOrderByCreatedAtDesc(String tenantId);

    /**
     * Find currently valid tariffs
     */
    @Query("SELECT t FROM Tariff t WHERE t.tenantId = :tenantId AND t.active = true " +
           "AND (t.validFrom IS NULL OR t.validFrom <= :now) " +
           "AND (t.validUntil IS NULL OR t.validUntil > :now) " +
           "ORDER BY t.defaultTariff DESC, t.createdAt DESC")
    List<Tariff> findCurrentlyValidTariffs(@Param("tenantId") String tenantId, @Param("now") Instant now);

    /**
     * Count active tariffs
     */
    long countByTenantIdAndActiveTrue(String tenantId);

    /**
     * Find tariffs by external ID
     */
    Optional<Tariff> findByTenantIdAndExternalTariffId(String tenantId, String externalTariffId);
}