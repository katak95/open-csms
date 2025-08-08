package com.opencsms.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter to extract and set tenant context from request.
 * Executes early in the filter chain to ensure tenant context is available.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_PARAM = "tenantId";
    
    private final TenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String tenantId = resolveTenant(request);
            
            if (tenantId != null) {
                log.debug("Setting tenant context: {}", tenantId);
                TenantContext.setCurrentTenant(tenantId);
            } else if (!isExcludedPath(request)) {
                log.warn("No tenant found for request: {} {}", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant identification required");
                return;
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            TenantContext.clear();
        }
    }
    
    private String resolveTenant(HttpServletRequest request) {
        // Priority 1: Header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        
        // Priority 2: Query parameter
        tenantId = request.getParameter(TENANT_PARAM);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        
        // Priority 3: Domain-based resolution
        tenantId = tenantResolver.resolveFromDomain(request);
        if (tenantId != null) {
            return tenantId;
        }
        
        // Priority 4: JWT token
        tenantId = tenantResolver.resolveFromToken(request);
        if (tenantId != null) {
            return tenantId;
        }
        
        // Priority 5: Path-based resolution (e.g., /api/tenants/{tenantId}/...)
        tenantId = tenantResolver.resolveFromPath(request);
        
        return tenantId;
    }
    
    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") ||
               path.startsWith("/health") ||
               path.startsWith("/metrics") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/auth/login") ||
               path.startsWith("/auth/register") ||
               path.equals("/") ||
               path.startsWith("/public");
    }
}