package com.opencsms.ocpp.config;

import com.opencsms.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor for OCPP WebSocket handshake to extract station information and tenant context.
 */
@Slf4j
public class OcppHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        String path = request.getURI().getPath();
        log.debug("OCPP handshake for path: {}", path);
        
        // Extract station ID from path
        String stationId = extractStationId(path);
        if (stationId == null) {
            log.warn("Invalid OCPP endpoint path: {}", path);
            return false;
        }
        
        attributes.put("stationId", stationId);
        
        // Determine OCPP version from path
        String ocppVersion = path.startsWith("/ocpp2/") ? "2.0.1" : "1.6";
        attributes.put("ocppVersion", ocppVersion);
        
        // Extract tenant ID from various sources
        String tenantId = extractTenantId(request);
        if (tenantId != null) {
            attributes.put("tenantId", tenantId);
            TenantContext.setCurrentTenant(tenantId);
        }
        
        // Extract authorization info if present
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null) {
            attributes.put("authorization", authorization);
        }
        
        // Add client IP
        String clientIp = getClientIp(request);
        attributes.put("clientIp", clientIp);
        
        log.info("OCPP handshake accepted - Station: {}, Version: {}, Tenant: {}, IP: {}", 
                stationId, ocppVersion, tenantId, clientIp);
        
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("OCPP handshake failed", exception);
        }
        // Clean up tenant context
        TenantContext.clear();
    }

    private String extractStationId(String path) {
        // Extract station ID from paths like /ocpp/{stationId} or /ocpp2/{stationId}
        String[] segments = path.split("/");
        if (segments.length >= 3) {
            return segments[segments.length - 1]; // Last segment
        }
        return null;
    }

    private String extractTenantId(ServerHttpRequest request) {
        // Try multiple sources for tenant ID
        String tenantId = null;
        
        // 1. From header
        tenantId = request.getHeaders().getFirst("X-Tenant-ID");
        if (tenantId != null) {
            return tenantId;
        }
        
        // 2. From query parameter
        tenantId = request.getURI().getQuery();
        if (tenantId != null && tenantId.contains("tenant=")) {
            String[] params = tenantId.split("&");
            for (String param : params) {
                if (param.startsWith("tenant=")) {
                    return param.substring(7); // Remove "tenant="
                }
            }
        }
        
        // 3. From subdomain (if applicable)
        String host = request.getHeaders().getFirst("Host");
        if (host != null && host.contains(".")) {
            String[] parts = host.split("\\.");
            if (parts.length > 2) {
                return parts[0]; // First part as tenant
            }
        }
        
        return null;
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}