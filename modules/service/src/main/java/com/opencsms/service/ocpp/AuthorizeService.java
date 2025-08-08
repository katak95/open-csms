package com.opencsms.service.ocpp;

import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import com.opencsms.service.core.TenantContext;
import com.opencsms.service.user.AuthTokenService;
import com.opencsms.service.user.UserService;
import com.opencsms.service.ocpp.model.ParsedOcppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service handling OCPP Authorize messages for token validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthorizeService {

    private final AuthTokenService authTokenService;
    private final UserService userService;

    /**
     * Process OCPP 1.6 Authorize message
     */
    public Map<String, Object> processAuthorize16(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            String idTag = (String) payload.get("idTag");

            log.info("Processing Authorize for station {} with idTag {}", stationSerial, idTag);

            // Validate the token
            AuthorizationResult result = validateToken(idTag);
            
            // Record authorization attempt
            recordAuthorizationAttempt(idTag, stationSerial, result.isValid());

            return createAuthorizeResponse(result);

        } catch (Exception e) {
            log.error("Error processing Authorize for station {}: {}", stationSerial, e.getMessage(), e);
            return createAuthorizeResponse(new AuthorizationResult(false, "Invalid", "InternalError", null));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Process OCPP 2.0.1 Authorize message
     */
    public Map<String, Object> processAuthorize201(ParsedOcppMessage message) {
        String stationSerial = message.getStationSerial();
        String tenantId = message.getTenantId();
        
        TenantContext.setCurrentTenantId(tenantId);
        
        try {
            Map<String, Object> payload = message.getPayload();
            Map<String, Object> idToken = (Map<String, Object>) payload.get("idToken");
            
            String idTokenValue = (String) idToken.get("idToken");
            String idTokenType = (String) idToken.get("type");

            log.info("Processing OCPP 2.0.1 Authorize for station {} with idToken {} type {}", 
                    stationSerial, idTokenValue, idTokenType);

            // Validate the token
            AuthorizationResult result = validateToken(idTokenValue);
            
            // Record authorization attempt
            recordAuthorizationAttempt(idTokenValue, stationSerial, result.isValid());

            return createAuthorize201Response(result);

        } catch (Exception e) {
            log.error("Error processing OCPP 2.0.1 Authorize for station {}: {}", stationSerial, e.getMessage(), e);
            return createAuthorize201Response(new AuthorizationResult(false, "Invalid", "InternalError", null));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Validate authorization token
     */
    private AuthorizationResult validateToken(String tokenValue) {
        try {
            // Find the token
            Optional<AuthToken> authTokenOpt = authTokenService.findValidTokenByValue(tokenValue);
            if (authTokenOpt.isEmpty()) {
                log.warn("Token not found: {}", tokenValue);
                return new AuthorizationResult(false, "Invalid", "UnknownToken", null);
            }

            AuthToken authToken = authTokenOpt.get();

            // Check token status
            if (authToken.getStatus() != AuthToken.TokenStatus.ACTIVE) {
                log.warn("Token not active: {} - status: {}", tokenValue, authToken.getStatus());
                return new AuthorizationResult(false, getStatusFromTokenStatus(authToken.getStatus()), 
                                             "TokenNotActive", authToken);
            }

            // Check token validity period
            if (authToken.getValidFrom() != null && Instant.now().isBefore(authToken.getValidFrom())) {
                log.warn("Token not yet valid: {} - valid from: {}", tokenValue, authToken.getValidFrom());
                return new AuthorizationResult(false, "Invalid", "TokenNotYetValid", authToken);
            }

            if (authToken.getValidUntil() != null && Instant.now().isAfter(authToken.getValidUntil())) {
                log.warn("Token expired: {} - valid until: {}", tokenValue, authToken.getValidUntil());
                return new AuthorizationResult(false, "Expired", "TokenExpired", authToken);
            }

            // Check usage limits
            if (authToken.getMaxUsages() != null && 
                authToken.getTotalUsages() != null && 
                authToken.getTotalUsages() >= authToken.getMaxUsages()) {
                log.warn("Token usage limit exceeded: {} - used: {}/{}", 
                        tokenValue, authToken.getTotalUsages(), authToken.getMaxUsages());
                return new AuthorizationResult(false, "Invalid", "UsageLimitExceeded", authToken);
            }

            // Validate associated user
            if (authToken.getUserId() != null) {
                try {
                    User user = userService.getUserById(authToken.getUserId());
                    
                    if (user.getStatus() != User.UserStatus.ACTIVE) {
                        log.warn("User not active for token: {} - user status: {}", tokenValue, user.getStatus());
                        return new AuthorizationResult(false, "Blocked", "UserNotActive", authToken);
                    }
                    
                } catch (Exception e) {
                    log.warn("User not found for token: {} - user ID: {}", tokenValue, authToken.getUserId());
                    return new AuthorizationResult(false, "Invalid", "UserNotFound", authToken);
                }
            }

            // Token is valid
            log.info("Token authorized successfully: {}", tokenValue);
            return new AuthorizationResult(true, "Accepted", null, authToken);

        } catch (Exception e) {
            log.error("Error validating token {}: {}", tokenValue, e.getMessage(), e);
            return new AuthorizationResult(false, "Invalid", "InternalError", null);
        }
    }

    private String getStatusFromTokenStatus(AuthToken.TokenStatus status) {
        switch (status) {
            case BLOCKED:
                return "Blocked";
            case SUSPENDED:
                return "Blocked";
            case EXPIRED:
                return "Expired";
            case PENDING:
                return "Invalid";
            default:
                return "Invalid";
        }
    }

    private void recordAuthorizationAttempt(String tokenValue, String stationSerial, boolean success) {
        try {
            // Record the authorization attempt for audit/statistics
            log.info("Authorization attempt: token={}, station={}, success={}", 
                    tokenValue, stationSerial, success);
            
            // Update token usage if successful
            if (success) {
                authTokenService.recordTokenUsage(tokenValue);
            }
            
        } catch (Exception e) {
            log.warn("Error recording authorization attempt: {}", e.getMessage());
        }
    }

    private Map<String, Object> createAuthorizeResponse(AuthorizationResult result) {
        Map<String, Object> idTagInfo = Map.of("status", result.getStatus());
        
        if (result.getStatusReason() != null) {
            idTagInfo = Map.of(
                "status", result.getStatus(),
                "statusReason", result.getStatusReason()
            );
        }

        // Add parent ID tag if available
        if (result.getAuthToken() != null && result.getAuthToken().getParentIdTag() != null) {
            idTagInfo = Map.of(
                "status", result.getStatus(),
                "parentIdTag", result.getAuthToken().getParentIdTag()
            );
        }

        return Map.of("idTagInfo", idTagInfo);
    }

    private Map<String, Object> createAuthorize201Response(AuthorizationResult result) {
        Map<String, Object> idTokenInfo = Map.of("status", result.getStatus());
        
        if (result.getStatusReason() != null) {
            idTokenInfo = Map.of(
                "status", result.getStatus(),
                "statusReason", result.getStatusReason()
            );
        }

        return Map.of("idTokenInfo", idTokenInfo);
    }

    /**
     * Authorization result wrapper
     */
    private static class AuthorizationResult {
        private final boolean valid;
        private final String status;
        private final String statusReason;
        private final AuthToken authToken;

        public AuthorizationResult(boolean valid, String status, String statusReason, AuthToken authToken) {
            this.valid = valid;
            this.status = status;
            this.statusReason = statusReason;
            this.authToken = authToken;
        }

        public boolean isValid() { return valid; }
        public String getStatus() { return status; }
        public String getStatusReason() { return statusReason; }
        public AuthToken getAuthToken() { return authToken; }
    }
}