package com.opencsms.core.tenant;

import com.opencsms.domain.tenant.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tenant operations.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    
    Optional<Tenant> findById(UUID id);
    
    Optional<Tenant> findByCode(String code);
    
    Optional<Tenant> findByCodeAndActiveTrue(String code);
    
    boolean existsByCode(String code);
    
    boolean existsByCodeAndActiveTrue(String code);
    
    @Query("SELECT t FROM Tenant t WHERE KEY(t.metadata) = 'custom_domain' AND VALUE(t.metadata) = :domain AND t.active = true")
    Optional<Tenant> findByCustomDomain(@Param("domain") String domain);
    
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.active = true")
    long countActiveTenants();
    
    @Query("SELECT t FROM Tenant t WHERE t.tenantType = :type AND t.active = true")
    List<Tenant> findByTenantTypeAndActive(@Param("type") Tenant.TenantType type);
    
    @Query("SELECT t FROM Tenant t WHERE :feature MEMBER OF t.enabledFeatures AND t.active = true")
    List<Tenant> findByEnabledFeature(@Param("feature") Tenant.TenantFeature feature);
}