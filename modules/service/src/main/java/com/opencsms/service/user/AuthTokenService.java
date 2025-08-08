package com.opencsms.service.user;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing authentication tokens (RFID, NFC, API keys, etc.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthTokenService {

    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserEventService userEventService;
    
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new authentication token for a user.
     */
    @Transactional
    public AuthToken createAuthToken(UUID userId, AuthToken.TokenType tokenType, 
                                   String tokenValue, String name, String description) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating auth token: {} for user: {} (type: {})", name, userId, tokenType);
        
        // Find the user
        User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Validate token value if provided, otherwise generate one
        if (!StringUtils.hasText(tokenValue)) {
            tokenValue = generateTokenValue(tokenType);
        } else {
            validateTokenValue(tokenValue, tokenType);
        }
        
        // Check for duplicate token values
        if (authTokenRepository.existsByTokenValueAndTenantId(tokenValue, tenantId)) {
            throw new AuthTokenServiceException("Token value already exists: " + tokenValue);
        }
        
        // Create the auth token
        AuthToken authToken = AuthToken.builder()
            .user(user)
            .tokenType(tokenType)
            .tokenValue(tokenValue)
            .tokenHash(passwordEncoder.encode(tokenValue))
            .name(name)
            .description(description)
            .tenantId(tenantId)
            .validFrom(Instant.now())
            .active(true)
            .build();
        
        AuthToken savedToken = authTokenRepository.save(authToken);
        
        // Record creation event
        userEventService.recordTokenCreated(savedToken);
        
        log.info("Created auth token: {} with ID: {}", savedToken.getName(), savedToken.getId());
        return savedToken;
    }

    /**
     * Create RFID token.
     */
    @Transactional
    public AuthToken createRfidToken(UUID userId, String rfidValue, String name) {
        return createAuthToken(userId, AuthToken.TokenType.RFID, rfidValue, name, "RFID card");
    }

    /**
     * Create NFC token.
     */
    @Transactional
    public AuthToken createNfcToken(UUID userId, String nfcValue, String name) {
        return createAuthToken(userId, AuthToken.TokenType.NFC, nfcValue, name, "NFC card/tag");
    }

    /**
     * Create API key.
     */
    @Transactional
    public AuthToken createApiKey(UUID userId, String name, String description) {
        return createAuthToken(userId, AuthToken.TokenType.API_KEY, null, name, description);
    }

    /**
     * Find token by ID within current tenant.
     */
    public Optional<AuthToken> findById(UUID tokenId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return authTokenRepository.findById(tokenId)
            .filter(token -> tenantId.equals(token.getTenantId()) && !token.isDeleted());
    }

    /**
     * Find token by value within current tenant.
     */
    public Optional<AuthToken> findByTokenValue(String tokenValue) {
        String tenantId = TenantContext.getCurrentTenantId();
        return authTokenRepository.findByTokenValueAndTenantId(tokenValue, tenantId)
            .filter(token -> !token.isDeleted());
    }

    /**
     * Find all tokens for a user.
     */
    public List<AuthToken> findByUser(UUID userId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return authTokenRepository.findByUserIdAndTenantIdAndDeletedFalse(userId, tenantId);
    }

    /**
     * Find tokens by type.
     */
    public List<AuthToken> findByTokenType(AuthToken.TokenType tokenType) {
        String tenantId = TenantContext.getCurrentTenantId();
        return authTokenRepository.findByTokenTypeAndTenantIdAndDeletedFalse(tokenType, tenantId);
    }

    /**
     * Find all active tokens.
     */
    public List<AuthToken> findAllActive() {
        String tenantId = TenantContext.getCurrentTenantId();
        return authTokenRepository.findByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
    }

    /**
     * Update token details.
     */
    @Transactional
    public AuthToken updateAuthToken(UUID tokenId, String name, String description, 
                                   Instant validUntil) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        if (StringUtils.hasText(name)) {
            token.setName(name);
        }
        
        if (StringUtils.hasText(description)) {
            token.setDescription(description);
        }
        
        if (validUntil != null) {
            token.setValidUntil(validUntil);
        }
        
        AuthToken updatedToken = authTokenRepository.save(token);
        
        // Record update event
        userEventService.recordTokenUpdated(updatedToken);
        
        log.info("Updated auth token: {}", updatedToken.getName());
        return updatedToken;
    }

    /**
     * Activate a token.
     */
    @Transactional
    public void activateToken(UUID tokenId) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        token.activate();
        authTokenRepository.save(token);
        
        // Record activation event
        userEventService.recordTokenActivated(token);
        
        log.info("Activated auth token: {}", token.getName());
    }

    /**
     * Deactivate a token.
     */
    @Transactional
    public void deactivateToken(UUID tokenId) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        token.deactivate();
        authTokenRepository.save(token);
        
        // Record deactivation event
        userEventService.recordTokenDeactivated(token);
        
        log.info("Deactivated auth token: {}", token.getName());
    }

    /**
     * Block a token with reason.
     */
    @Transactional
    public void blockToken(UUID tokenId, String reason) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        token.block(reason);
        authTokenRepository.save(token);
        
        // Record block event
        userEventService.recordTokenBlocked(token, reason);
        
        log.info("Blocked auth token: {} - {}", token.getName(), reason);
    }

    /**
     * Unblock a token.
     */
    @Transactional
    public void unblockToken(UUID tokenId) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        token.unblock();
        authTokenRepository.save(token);
        
        // Record unblock event
        userEventService.recordTokenUnblocked(token);
        
        log.info("Unblocked auth token: {}", token.getName());
    }

    /**
     * Delete a token (soft delete).
     */
    @Transactional
    public void deleteToken(UUID tokenId) {
        AuthToken token = findById(tokenId)
            .orElseThrow(() -> new AuthTokenNotFoundException("Token not found: " + tokenId));
        
        token.setDeleted(true);
        token.setDeletedAt(Instant.now());
        authTokenRepository.save(token);
        
        // Record deletion event
        userEventService.recordTokenDeleted(token);
        
        log.info("Deleted auth token: {}", token.getName());
    }

    /**
     * Verify token and record usage.
     */
    @Transactional
    public boolean verifyAndUseToken(String tokenValue) {
        Optional<AuthToken> tokenOpt = findByTokenValue(tokenValue);
        
        if (tokenOpt.isEmpty()) {
            log.debug("Token not found: {}", tokenValue);
            return false;
        }
        
        AuthToken token = tokenOpt.get();
        
        if (!token.isValid()) {
            log.debug("Token not valid: {} (active: {}, blocked: {}, expired: {})", 
                     tokenValue, token.isActive(), token.isBlocked(), token.isExpired());
            return false;
        }
        
        // Record usage
        token.recordUsage();
        authTokenRepository.save(token);
        
        // Record usage event
        userEventService.recordTokenUsed(token);
        
        log.debug("Token verified and used: {}", token.getName());
        return true;
    }

    /**
     * Get token statistics.
     */
    public AuthTokenStatistics getTokenStatistics() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        long totalTokens = authTokenRepository.countByTenantIdAndDeletedFalse(tenantId);
        long activeTokens = authTokenRepository.countByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
        long blockedTokens = authTokenRepository.countByTenantIdAndBlockedTrueAndDeletedFalse(tenantId);
        long expiredTokens = authTokenRepository.countExpiredTokens(tenantId, Instant.now());
        
        // Count by type
        var tokensByType = authTokenRepository.countByTokenTypeAndTenantId(tenantId);
        
        return AuthTokenStatistics.builder()
            .totalTokens(totalTokens)
            .activeTokens(activeTokens)
            .blockedTokens(blockedTokens)
            .expiredTokens(expiredTokens)
            .tokensByType(tokensByType)
            .lastUpdateTime(Instant.now())
            .build();
    }

    /**
     * Process expired tokens.
     */
    @Transactional
    public void processExpiredTokens() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        List<AuthToken> expiredTokens = authTokenRepository.findExpiredTokens(tenantId, Instant.now());
        
        for (AuthToken token : expiredTokens) {
            if (token.isActive()) {
                token.deactivate();
                authTokenRepository.save(token);
                
                log.info("Automatically deactivated expired token: {}", token.getName());
            }
        }
    }

    private String generateTokenValue(AuthToken.TokenType tokenType) {
        switch (tokenType) {
            case RFID:
            case NFC:
                return generateHexToken(8); // 8-byte hex string
            case API_KEY:
                return generateApiKey();
            case BARCODE:
                return generateBarcodeToken();
            default:
                return generateRandomToken(32);
        }
    }

    private String generateHexToken(int bytes) {
        byte[] tokenBytes = new byte[bytes];
        secureRandom.nextBytes(tokenBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : tokenBytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String generateApiKey() {
        // Generate API key in format: ocs_live_...
        String prefix = "ocs_live_";
        String randomPart = generateRandomToken(32);
        return prefix + randomPart;
    }

    private String generateBarcodeToken() {
        // Generate 12-digit numeric token
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String generateRandomToken(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void validateTokenValue(String tokenValue, AuthToken.TokenType tokenType) {
        if (!StringUtils.hasText(tokenValue)) {
            throw new AuthTokenServiceException("Token value cannot be empty");
        }
        
        if (tokenValue.length() > 255) {
            throw new AuthTokenServiceException("Token value too long (max 255 characters)");
        }
        
        switch (tokenType) {
            case RFID:
            case NFC:
                if (!tokenValue.matches("^[0-9A-Fa-f]+$")) {
                    throw new AuthTokenServiceException("RFID/NFC token must be hexadecimal");
                }
                break;
            case API_KEY:
                if (!tokenValue.startsWith("ocs_")) {
                    throw new AuthTokenServiceException("API key must start with 'ocs_'");
                }
                break;
            case BARCODE:
                if (!tokenValue.matches("^\\d+$")) {
                    throw new AuthTokenServiceException("Barcode token must be numeric");
                }
                break;
        }
    }

    /**
     * Repository interface for AuthToken.
     */
    @Repository
    public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
        
        Optional<AuthToken> findByTokenValueAndTenantId(String tokenValue, String tenantId);
        
        boolean existsByTokenValueAndTenantId(String tokenValue, String tenantId);
        
        List<AuthToken> findByUserIdAndTenantIdAndDeletedFalse(UUID userId, String tenantId);
        
        List<AuthToken> findByTokenTypeAndTenantIdAndDeletedFalse(AuthToken.TokenType tokenType, String tenantId);
        
        List<AuthToken> findByTenantIdAndActiveTrueAndDeletedFalse(String tenantId);
        
        long countByTenantIdAndDeletedFalse(String tenantId);
        
        long countByTenantIdAndActiveTrueAndDeletedFalse(String tenantId);
        
        long countByTenantIdAndBlockedTrueAndDeletedFalse(String tenantId);
        
        @Query("SELECT COUNT(t) FROM AuthToken t WHERE t.tenantId = :tenantId AND t.validUntil IS NOT NULL AND t.validUntil < :now AND t.deleted = false")
        long countExpiredTokens(@Param("tenantId") String tenantId, @Param("now") Instant now);
        
        @Query("SELECT t FROM AuthToken t WHERE t.tenantId = :tenantId AND t.validUntil IS NOT NULL AND t.validUntil < :now AND t.deleted = false")
        List<AuthToken> findExpiredTokens(@Param("tenantId") String tenantId, @Param("now") Instant now);
        
        @Query("SELECT t.tokenType as tokenType, COUNT(t) as count FROM AuthToken t WHERE t.tenantId = :tenantId AND t.deleted = false GROUP BY t.tokenType")
        List<TokenTypeCount> countByTokenTypeAndTenantId(@Param("tenantId") String tenantId);
        
        interface TokenTypeCount {
            AuthToken.TokenType getTokenType();
            long getCount();
        }
    }

    /**
     * Auth token statistics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthTokenStatistics {
        private long totalTokens;
        private long activeTokens;
        private long blockedTokens;
        private long expiredTokens;
        private List<AuthTokenRepository.TokenTypeCount> tokensByType;
        private Instant lastUpdateTime;
        
        public double getActivePercentage() {
            return totalTokens > 0 ? (double) activeTokens / totalTokens * 100.0 : 100.0;
        }
        
        public double getBlockedPercentage() {
            return totalTokens > 0 ? (double) blockedTokens / totalTokens * 100.0 : 0.0;
        }
        
        public double getExpiredPercentage() {
            return totalTokens > 0 ? (double) expiredTokens / totalTokens * 100.0 : 0.0;
        }
    }
}