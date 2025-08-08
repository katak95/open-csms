package com.opencsms.core.tenant;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves tenant ID from various sources in HTTP requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantResolver {
    
    private static final Pattern PATH_TENANT_PATTERN = Pattern.compile("/api/tenants/([^/]+)/.*");
    private static final Pattern SUBDOMAIN_PATTERN = Pattern.compile("^([^.]+)\\..*");
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    @Value("${app.multi-tenant.domain-strategy:false}")
    private boolean domainStrategy;
    
    @Value("${app.multi-tenant.default-tenant:}")
    private String defaultTenant;
    
    private final TenantService tenantService;
    
    /**
     * Resolve tenant from domain/subdomain.
     * Example: tenant1.opencsms.io -> tenant1
     */
    @Cacheable(value = "tenant-domain", key = "#request.serverName")
    public String resolveFromDomain(HttpServletRequest request) {
        if (!domainStrategy) {
            return null;
        }
        
        String serverName = request.getServerName();
        if (serverName == null || serverName.equals("localhost")) {
            return defaultTenant;
        }
        
        // Try subdomain extraction
        Matcher matcher = SUBDOMAIN_PATTERN.matcher(serverName);
        if (matcher.matches()) {
            String subdomain = matcher.group(1);
            if (!subdomain.equals("www") && !subdomain.equals("api")) {
                // Validate tenant exists
                if (tenantService.exists(subdomain)) {
                    return subdomain;
                }
            }
        }
        
        // Try custom domain mapping
        String tenantId = tenantService.findByCustomDomain(serverName);
        if (tenantId != null) {
            return tenantId;
        }
        
        return null;
    }
    
    /**
     * Resolve tenant from JWT token.
     */
    public String resolveFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // This will be implemented when JWT is set up
                // For now, return null
                return null;
            } catch (Exception e) {
                log.debug("Failed to extract tenant from token", e);
            }
        }
        return null;
    }
    
    /**
     * Resolve tenant from URL path.
     * Example: /api/tenants/tenant1/stations -> tenant1
     */
    public String resolveFromPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        Matcher matcher = PATH_TENANT_PATTERN.matcher(path);
        if (matcher.matches()) {
            String tenantId = matcher.group(1);
            // Validate tenant exists
            if (tenantService.exists(tenantId)) {
                return tenantId;
            }
        }
        
        return null;
    }
}