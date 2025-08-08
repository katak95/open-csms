package com.opencsms.web.controller;

import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import com.opencsms.service.user.AuthTokenService;
import com.opencsms.service.user.UserService;
import com.opencsms.web.dto.AuthTokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for authentication token management.
 */
@RestController
@RequestMapping("/api/v1/auth-tokens")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AuthTokenController {

    private final AuthTokenService authTokenService;
    private final UserService userService;

    /**
     * Get all auth tokens.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TOKEN:READ')")
    public ResponseEntity<List<AuthTokenDto>> getAllAuthTokens() {
        log.debug("Getting all auth tokens");
        
        List<AuthToken> tokens = authTokenService.findAllActive();
        List<AuthTokenDto> tokenDtos = tokens.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(tokenDtos);
    }

    /**
     * Get auth tokens for current user.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AuthTokenDto>> getCurrentUserAuthTokens(HttpServletRequest request) {
        String username = request.getUserPrincipal().getName();
        log.debug("Getting auth tokens for current user: {}", username);
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        List<AuthToken> tokens = authTokenService.findByUser(currentUser.getId());
        List<AuthTokenDto> tokenDtos = tokens.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(tokenDtos);
    }

    /**
     * Get auth tokens for a specific user.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('TOKEN:READ')")
    public ResponseEntity<List<AuthTokenDto>> getUserAuthTokens(@PathVariable UUID userId) {
        log.debug("Getting auth tokens for user: {}", userId);
        
        List<AuthToken> tokens = authTokenService.findByUser(userId);
        List<AuthTokenDto> tokenDtos = tokens.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(tokenDtos);
    }

    /**
     * Get auth token by ID.
     */
    @GetMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('TOKEN:READ')")
    public ResponseEntity<AuthTokenDto> getAuthTokenById(@PathVariable UUID tokenId) {
        log.debug("Getting auth token by ID: {}", tokenId);
        
        return authTokenService.findById(tokenId)
            .map(token -> ResponseEntity.ok(convertToDto(token)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get auth tokens by type.
     */
    @GetMapping("/type/{tokenType}")
    @PreAuthorize("hasAuthority('TOKEN:READ')")
    public ResponseEntity<List<AuthTokenDto>> getAuthTokensByType(@PathVariable AuthToken.TokenType tokenType) {
        log.debug("Getting auth tokens by type: {}", tokenType);
        
        List<AuthToken> tokens = authTokenService.findByTokenType(tokenType);
        List<AuthTokenDto> tokenDtos = tokens.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(tokenDtos);
    }

    /**
     * Create a new auth token for current user.
     */
    @PostMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthTokenDto> createAuthTokenForCurrentUser(
            @Valid @RequestBody AuthTokenDto.CreateAuthTokenRequest request,
            HttpServletRequest servletRequest) {
        
        String username = servletRequest.getUserPrincipal().getName();
        log.info("Creating auth token for current user: {} - type: {}", username, request.getTokenType());
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        AuthToken createdToken = authTokenService.createAuthToken(
            currentUser.getId(),
            request.getTokenType(),
            request.getTokenValue(),
            request.getName(),
            request.getDescription()
        );
        
        // Update validity period if specified
        if (request.getValidUntil() != null) {
            createdToken = authTokenService.updateAuthToken(
                createdToken.getId(),
                null,
                null,
                request.getValidUntil()
            );
        }
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDtoWithValue(createdToken));
    }

    /**
     * Create a new auth token for a specific user.
     */
    @PostMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('TOKEN:CREATE')")
    public ResponseEntity<AuthTokenDto> createAuthTokenForUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AuthTokenDto.CreateAuthTokenRequest request) {
        
        log.info("Creating auth token for user: {} - type: {}", userId, request.getTokenType());
        
        AuthToken createdToken = authTokenService.createAuthToken(
            userId,
            request.getTokenType(),
            request.getTokenValue(),
            request.getName(),
            request.getDescription()
        );
        
        // Update validity period if specified
        if (request.getValidUntil() != null) {
            createdToken = authTokenService.updateAuthToken(
                createdToken.getId(),
                null,
                null,
                request.getValidUntil()
            );
        }
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDtoWithValue(createdToken));
    }

    /**
     * Create RFID token for current user.
     */
    @PostMapping("/me/rfid")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthTokenDto> createRfidTokenForCurrentUser(
            @RequestParam String rfidValue,
            @RequestParam String name,
            HttpServletRequest servletRequest) {
        
        String username = servletRequest.getUserPrincipal().getName();
        log.info("Creating RFID token for current user: {} - {}", username, name);
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        AuthToken createdToken = authTokenService.createRfidToken(currentUser.getId(), rfidValue, name);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDtoWithValue(createdToken));
    }

    /**
     * Create NFC token for current user.
     */
    @PostMapping("/me/nfc")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthTokenDto> createNfcTokenForCurrentUser(
            @RequestParam String nfcValue,
            @RequestParam String name,
            HttpServletRequest servletRequest) {
        
        String username = servletRequest.getUserPrincipal().getName();
        log.info("Creating NFC token for current user: {} - {}", username, name);
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        AuthToken createdToken = authTokenService.createNfcToken(currentUser.getId(), nfcValue, name);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDtoWithValue(createdToken));
    }

    /**
     * Create API key for current user.
     */
    @PostMapping("/me/api-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthTokenDto> createApiKeyForCurrentUser(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            HttpServletRequest servletRequest) {
        
        String username = servletRequest.getUserPrincipal().getName();
        log.info("Creating API key for current user: {} - {}", username, name);
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        AuthToken createdToken = authTokenService.createApiKey(currentUser.getId(), name, description);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDtoWithValue(createdToken));
    }

    /**
     * Update auth token.
     */
    @PutMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('TOKEN:UPDATE')")
    public ResponseEntity<AuthTokenDto> updateAuthToken(
            @PathVariable UUID tokenId,
            @Valid @RequestBody AuthTokenDto.UpdateAuthTokenRequest request) {
        
        log.debug("Updating auth token: {}", tokenId);
        
        AuthToken updatedToken = authTokenService.updateAuthToken(
            tokenId,
            request.getName(),
            request.getDescription(),
            request.getValidUntil()
        );
        
        return ResponseEntity.ok(convertToDto(updatedToken));
    }

    /**
     * Update auth token status.
     */
    @PostMapping("/{tokenId}/status")
    @PreAuthorize("hasAuthority('TOKEN:UPDATE')")
    public ResponseEntity<Void> updateAuthTokenStatus(
            @PathVariable UUID tokenId,
            @Valid @RequestBody AuthTokenDto.AuthTokenStatusRequest request) {
        
        log.debug("Updating auth token status: {} - {}", tokenId, request.getAction());
        
        switch (request.getAction()) {
            case ACTIVATE:
                authTokenService.activateToken(tokenId);
                break;
            case DEACTIVATE:
                authTokenService.deactivateToken(tokenId);
                break;
            case BLOCK:
                authTokenService.blockToken(tokenId, request.getReason());
                break;
            case UNBLOCK:
                authTokenService.unblockToken(tokenId);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * Delete auth token.
     */
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('TOKEN:DELETE')")
    public ResponseEntity<Void> deleteAuthToken(@PathVariable UUID tokenId) {
        log.debug("Deleting auth token: {}", tokenId);
        
        authTokenService.deleteToken(tokenId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify auth token.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('TOKEN:VERIFY')")
    public ResponseEntity<AuthTokenDto.TokenVerificationResponse> verifyAuthToken(
            @Valid @RequestBody AuthTokenDto.TokenVerificationRequest request) {
        
        log.debug("Verifying auth token");
        
        boolean isValid = authTokenService.verifyAndUseToken(request.getTokenValue());
        
        if (!isValid) {
            return ResponseEntity.ok(AuthTokenDto.TokenVerificationResponse.builder()
                .valid(false)
                .message("Invalid token")
                .build());
        }
        
        // Get token details for response
        return authTokenService.findByTokenValue(request.getTokenValue())
            .map(token -> ResponseEntity.ok(AuthTokenDto.TokenVerificationResponse.builder()
                .valid(true)
                .message("Token is valid")
                .userId(token.getUser().getId())
                .username(token.getUser().getUsername())
                .tokenType(token.getTokenType())
                .lastUsedAt(token.getLastUsedAt())
                .usageCount(token.getUsageCount())
                .build()))
            .orElse(ResponseEntity.ok(AuthTokenDto.TokenVerificationResponse.builder()
                .valid(false)
                .message("Token not found")
                .build()));
    }

    /**
     * Get auth token statistics.
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('TOKEN:READ')")
    public ResponseEntity<AuthTokenService.AuthTokenStatistics> getAuthTokenStatistics() {
        log.debug("Getting auth token statistics");
        
        AuthTokenService.AuthTokenStatistics statistics = authTokenService.getTokenStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * Process expired tokens (admin utility endpoint).
     */
    @PostMapping("/process-expired")
    @PreAuthorize("hasAuthority('TOKEN:ADMIN')")
    public ResponseEntity<Void> processExpiredTokens() {
        log.debug("Processing expired auth tokens");
        
        authTokenService.processExpiredTokens();
        return ResponseEntity.ok().build();
    }

    // Helper methods for DTO conversion
    private AuthTokenDto convertToDto(AuthToken token) {
        return AuthTokenDto.builder()
            .id(token.getId())
            .tokenType(token.getTokenType())
            // Don't include token value in regular responses for security
            .name(token.getName())
            .description(token.getDescription())
            .validFrom(token.getValidFrom())
            .validUntil(token.getValidUntil())
            .lastUsedAt(token.getLastUsedAt())
            .usageCount(token.getUsageCount())
            .active(token.isActive())
            .blocked(token.isBlocked())
            .blockReason(token.getBlockReason())
            .metadata(token.getMetadata())
            .createdAt(token.getCreatedAt())
            .updatedAt(token.getUpdatedAt())
            .build();
    }

    private AuthTokenDto convertToDtoWithValue(AuthToken token) {
        AuthTokenDto dto = convertToDto(token);
        // Include token value only when creating new tokens
        dto.setTokenValue(token.getTokenValue());
        return dto;
    }
}