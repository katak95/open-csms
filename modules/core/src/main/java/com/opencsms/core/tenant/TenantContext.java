package com.opencsms.core.tenant;

/**
 * Thread-local storage for current tenant context.
 * Used throughout the application to ensure tenant isolation.
 */
public class TenantContext {
    
    private static final ThreadLocal<String> currentTenant = new InheritableThreadLocal<>();
    
    private TenantContext() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Set the current tenant ID for this thread.
     * 
     * @param tenantId the tenant identifier
     */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        currentTenant.set(tenantId);
    }
    
    /**
     * Get the current tenant ID for this thread.
     * 
     * @return the current tenant ID
     * @throws IllegalStateException if no tenant is set
     */
    public static String getCurrentTenant() {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant set in current context");
        }
        return tenantId;
    }
    
    /**
     * Get the current tenant ID for this thread, or null if not set.
     * 
     * @return the current tenant ID or null
     */
    public static String getCurrentTenantOrNull() {
        return currentTenant.get();
    }
    
    /**
     * Check if a tenant is set in the current context.
     * 
     * @return true if a tenant is set
     */
    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }
    
    /**
     * Clear the current tenant context.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        currentTenant.remove();
    }
}