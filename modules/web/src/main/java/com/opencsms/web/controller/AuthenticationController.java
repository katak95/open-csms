package com.opencsms.web.controller;

import com.opencsms.security.service.MultiProviderAuthenticationService;
import com.opencsms.security.service.OidcProviderService;
import com.opencsms.web.dto.AuthenticationDto;
import com.opencsms.web.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AuthenticationController {

    private final MultiProviderAuthenticationService authService;
    private final OidcProviderService oidcService;

    /**
     * Authenticate user with username/password.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationDto.LoginRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("Login attempt for user: {} from IP: {}", request.getUsernameOrEmail(), ipAddress);
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.authenticateWithPassword(request.getUsernameOrEmail(), request.getPassword(), ipAddress);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Authenticate user with RFID token.
     */
    @PostMapping("/rfid")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> authenticateWithRfid(
            @Valid @RequestBody AuthenticationDto.RfidAuthRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("RFID authentication attempt from IP: {}", ipAddress);
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.authenticateWithRfid(request.getRfidValue(), ipAddress);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Authenticate user with NFC token.
     */
    @PostMapping("/nfc")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> authenticateWithNfc(
            @Valid @RequestBody AuthenticationDto.NfcAuthRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("NFC authentication attempt from IP: {}", ipAddress);
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.authenticateWithNfc(request.getNfcValue(), ipAddress);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Authenticate user with API key.
     */
    @PostMapping("/api-key")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> authenticateWithApiKey(
            @Valid @RequestBody AuthenticationDto.ApiKeyAuthRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("API key authentication attempt from IP: {}", ipAddress);
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.authenticateWithApiKey(request.getApiKey(), ipAddress);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Authenticate user with mobile app token.
     */
    @PostMapping("/mobile")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> authenticateWithMobile(
            @Valid @RequestBody AuthenticationDto.MobileAuthRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("Mobile app authentication attempt from IP: {}", ipAddress);
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.authenticateWithMobileToken(request.getMobileToken(), ipAddress);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Refresh JWT token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> refreshToken(
            @Valid @RequestBody AuthenticationDto.RefreshTokenRequest request) {
        
        log.debug("Token refresh request");
        
        // Extract username from refresh token (simplified - in real implementation, 
        // refresh tokens should be stored and validated properly)
        String username = request.getRefreshToken(); // This is simplified
        
        MultiProviderAuthenticationService.AuthenticationResult result = 
            authService.refreshToken(username);
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Initialize OIDC authentication.
     */
    @PostMapping("/oidc/init")
    public ResponseEntity<AuthenticationDto.OidcAuthResponse> initOidcAuth(
            @Valid @RequestBody AuthenticationDto.OidcAuthRequest request) {
        
        log.info("Initializing OIDC authentication for provider: {}", request.getProvider());
        
        String state = UUID.randomUUID().toString();
        String authUrl;
        
        switch (request.getProvider()) {
            case GOOGLE:
                authUrl = oidcService.generateGoogleAuthUrl(state);
                break;
            case MICROSOFT:
                authUrl = oidcService.generateMicrosoftAuthUrl(state);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(AuthenticationDto.OidcAuthResponse.builder()
            .authorizationUrl(authUrl)
            .state(state)
            .provider(request.getProvider())
            .build());
    }

    /**
     * Handle OIDC callback.
     */
    @PostMapping("/oidc/callback")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> handleOidcCallback(
            @Valid @RequestBody AuthenticationDto.OidcCallbackRequest request,
            HttpServletRequest servletRequest) {
        
        String ipAddress = getClientIpAddress(servletRequest);
        log.info("OIDC callback for provider: {} from IP: {}", request.getProvider(), ipAddress);
        
        if (request.getError() != null) {
            log.warn("OIDC callback error: {} - {}", request.getError(), request.getErrorDescription());
            
            return ResponseEntity.ok(AuthenticationDto.AuthenticationResponse.builder()
                .success(false)
                .message("OIDC authentication failed: " + request.getErrorDescription())
                .timestamp(Instant.now())
                .build());
        }
        
        MultiProviderAuthenticationService.AuthenticationResult result;
        
        switch (request.getProvider()) {
            case GOOGLE:
                result = oidcService.handleGoogleCallback(request.getCode(), request.getState(), ipAddress);
                break;
            case MICROSOFT:
                result = oidcService.handleMicrosoftCallback(request.getCode(), request.getState(), ipAddress);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(convertToResponse(result));
    }

    /**
     * Logout user.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthenticationDto.LogoutRequest request) {
        log.debug("User logout request");
        
        // TODO: Implement proper logout functionality
        // - Invalidate JWT token (add to blacklist)
        // - Clear session data
        // - If logoutAllSessions is true, invalidate all user sessions
        
        return ResponseEntity.ok().build();
    }

    /**
     * Request password reset.
     */
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody AuthenticationDto.PasswordResetRequest request) {
        
        log.info("Password reset request for email: {}", request.getEmail());
        
        // TODO: Implement password reset functionality
        // - Generate reset token
        // - Send reset email
        // - Store reset token with expiration
        
        return ResponseEntity.ok().build();
    }

    /**
     * Confirm password reset.
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody AuthenticationDto.PasswordResetConfirmRequest request) {
        
        log.info("Password reset confirmation");
        
        // TODO: Implement password reset confirmation
        // - Validate reset token
        // - Update user password
        // - Invalidate reset token
        
        return ResponseEntity.ok().build();
    }

    /**
     * Get current authentication status.
     */
    @GetMapping("/status")
    public ResponseEntity<AuthenticationDto.AuthenticationResponse> getAuthStatus(
            HttpServletRequest request) {
        
        if (request.getUserPrincipal() != null) {
            return ResponseEntity.ok(AuthenticationDto.AuthenticationResponse.builder()
                .success(true)
                .message("Authenticated")
                .timestamp(Instant.now())
                .build());
        } else {
            return ResponseEntity.ok(AuthenticationDto.AuthenticationResponse.builder()
                .success(false)
                .message("Not authenticated")
                .timestamp(Instant.now())
                .build());
        }
    }

    // Helper methods
    private AuthenticationDto.AuthenticationResponse convertToResponse(
            MultiProviderAuthenticationService.AuthenticationResult result) {
        
        UserDto userDto = null;
        if (result.getUser() != null) {
            userDto = convertUserToDto(result.getUser());
        }
        
        return AuthenticationDto.AuthenticationResponse.builder()
            .success(result.isSuccess())
            .message(result.getMessage())
            .accessToken(result.getAuthToken())
            .authMethod(result.getAuthMethod())
            .user(userDto)
            .timestamp(result.getTimestamp())
            .build();
    }

    private UserDto convertUserToDto(com.opencsms.domain.user.User user) {
        return UserDto.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .displayName(user.getDisplayName())
            .emailVerified(user.isEmailVerified())
            .language(user.getLanguage())
            .timezone(user.getTimezone())
            .active(user.isActive())
            .suspended(user.isSuspended())
            .build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
}