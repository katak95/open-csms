package com.opencsms.core.tenant;

import com.opencsms.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for tenant management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenantService {
    
    private final TenantRepository tenantRepository;
    
    /**
     * Check if a tenant exists by code.
     */
    @Cacheable(value = "tenant-exists", key = "#tenantCode")
    public boolean exists(String tenantCode) {
        return tenantRepository.existsByCodeAndActiveTrue(tenantCode);
    }
    
    /**
     * Find tenant by custom domain.
     */
    @Cacheable(value = "tenant-domain", key = "#domain")
    public String findByCustomDomain(String domain) {
        return tenantRepository.findByCustomDomain(domain)
            .map(Tenant::getCode)
            .orElse(null);
    }
    
    /**
     * Get tenant by code.
     */
    @Cacheable(value = "tenants", key = "#code")
    public Optional<Tenant> findByCode(String code) {
        return tenantRepository.findByCodeAndActiveTrue(code);
    }
    
    /**
     * Get tenant by ID.
     */
    @Cacheable(value = "tenants", key = "#id")
    public Optional<Tenant> findById(String id) {
        return tenantRepository.findById(id);
    }
    
    /**
     * Create a new tenant.
     */
    @Transactional
    @CacheEvict(value = {"tenants", "tenant-exists", "tenant-domain"}, allEntries = true)
    public Tenant create(Tenant tenant) {
        if (tenantRepository.existsByCode(tenant.getCode())) {
            throw new IllegalArgumentException("Tenant with code " + tenant.getCode() + " already exists");
        }
        
        log.info("Creating new tenant: {}", tenant.getCode());
        return tenantRepository.save(tenant);
    }
    
    /**
     * Update an existing tenant.
     */
    @Transactional
    @CacheEvict(value = {"tenants", "tenant-exists", "tenant-domain"}, allEntries = true)
    public Tenant update(Tenant tenant) {
        log.info("Updating tenant: {}", tenant.getCode());
        return tenantRepository.save(tenant);
    }
    
    /**
     * Activate a tenant.
     */
    @Transactional
    @CacheEvict(value = {"tenants", "tenant-exists"}, key = "#code")
    public void activate(String code) {
        Tenant tenant = tenantRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + code));
        
        tenant.activate();
        tenantRepository.save(tenant);
        log.info("Activated tenant: {}", code);
    }
    
    /**
     * Suspend a tenant.
     */
    @Transactional
    @CacheEvict(value = {"tenants", "tenant-exists"}, key = "#code")
    public void suspend(String code, String reason) {
        Tenant tenant = tenantRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + code));
        
        tenant.suspend(reason);
        tenantRepository.save(tenant);
        log.warn("Suspended tenant: {} - Reason: {}", code, reason);
    }
    
    /**
     * Validate current tenant context.
     */
    public void validateCurrentTenant() {
        String tenantId = TenantContext.getCurrentTenant();
        if (!exists(tenantId)) {
            throw new IllegalStateException("Invalid or inactive tenant: " + tenantId);
        }
    }
}