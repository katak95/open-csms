package com.opencsms.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.security.service.MultiProviderAuthenticationService;
import lombok.Data;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * Authentication DTO for API requests and responses.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthenticationDto {

    /**
     * Login request DTO.
     */
    @Data
    public static class LoginRequest {
        
        @NotBlank(message = "Username or email is required")
        private String usernameOrEmail;
        
        @NotBlank(message = "Password is required")
        private String password;
        
        private String ipAddress;
        private boolean rememberMe = false;
    }

    /**
     * RFID authentication request DTO.
     */
    @Data
    public static class RfidAuthRequest {
        
        @NotBlank(message = "RFID value is required")
        @Pattern(regexp = "^[0-9A-Fa-f]+$", message = "RFID value must be hexadecimal")
        private String rfidValue;
        
        private String ipAddress;
    }

    /**
     * NFC authentication request DTO.
     */
    @Data
    public static class NfcAuthRequest {
        
        @NotBlank(message = "NFC value is required")
        @Pattern(regexp = "^[0-9A-Fa-f]+$", message = "NFC value must be hexadecimal")
        private String nfcValue;
        
        private String ipAddress;
    }

    /**
     * API key authentication request DTO.
     */
    @Data
    public static class ApiKeyAuthRequest {
        
        @NotBlank(message = "API key is required")
        private String apiKey;
        
        private String ipAddress;
    }

    /**
     * Mobile token authentication request DTO.
     */
    @Data
    public static class MobileAuthRequest {
        
        @NotBlank(message = "Mobile token is required")
        private String mobileToken;
        
        private String ipAddress;
    }

    /**
     * Token refresh request DTO.
     */
    @Data
    public static class RefreshTokenRequest {
        
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }

    /**
     * Authentication response DTO.
     */
    @Data
    @Builder
    public static class AuthenticationResponse {
        
        private boolean success;
        private String message;
        private String accessToken;
        private String refreshToken;
        private Instant expiresAt;
        private MultiProviderAuthenticationService.AuthenticationMethod authMethod;
        private UserDto user;
        private Instant timestamp;
    }

    /**
     * OIDC authorization request DTO.
     */
    @Data
    public static class OidcAuthRequest {
        
        @NotNull(message = "Provider is required")
        private OidcProvider provider;
        
        private String state;
        private String redirectUri;
        
        public enum OidcProvider {
            GOOGLE,
            MICROSOFT,
            GITHUB,
            GITLAB
        }
    }

    /**
     * OIDC callback request DTO.
     */
    @Data
    public static class OidcCallbackRequest {
        
        @NotNull(message = "Provider is required")
        private OidcAuthRequest.OidcProvider provider;
        
        @NotBlank(message = "Authorization code is required")
        private String code;
        
        private String state;
        private String error;
        private String errorDescription;
        private String ipAddress;
    }

    /**
     * OIDC authorization response DTO.
     */
    @Data
    @Builder
    public static class OidcAuthResponse {
        
        private String authorizationUrl;
        private String state;
        private OidcAuthRequest.OidcProvider provider;
    }

    /**
     * Logout request DTO.
     */
    @Data
    public static class LogoutRequest {
        
        private boolean logoutAllSessions = false;
    }

    /**
     * Password reset request DTO.
     */
    @Data
    public static class PasswordResetRequest {
        
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        private String email;
    }

    /**
     * Password reset confirmation DTO.
     */
    @Data
    public static class PasswordResetConfirmRequest {
        
        @NotBlank(message = "Reset token is required")
        private String resetToken;
        
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String newPassword;
    }
}