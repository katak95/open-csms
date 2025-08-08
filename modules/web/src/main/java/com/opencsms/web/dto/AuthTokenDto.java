package com.opencsms.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.domain.user.AuthToken;
import lombok.Data;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication token DTO for API responses and requests.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthTokenDto {

    private UUID id;
    
    @NotNull(message = "Token type is required")
    private AuthToken.TokenType tokenType;
    
    // Token value is only included when creating a new token
    private String tokenValue;
    
    @NotBlank(message = "Token name is required")
    @Size(max = 200, message = "Token name must be less than 200 characters")
    private String name;
    
    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;
    
    private Instant validFrom;
    private Instant validUntil;
    private Instant lastUsedAt;
    private long usageCount;
    
    private boolean active;
    private boolean blocked;
    private String blockReason;
    
    private Map<String, Object> metadata;
    
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Create auth token request DTO.
     */
    @Data
    @Builder
    public static class CreateAuthTokenRequest {
        
        @NotNull(message = "Token type is required")
        private AuthToken.TokenType tokenType;
        
        // Optional - if not provided, a token value will be generated
        @Size(max = 255, message = "Token value must be less than 255 characters")
        private String tokenValue;
        
        @NotBlank(message = "Token name is required")
        @Size(max = 200, message = "Token name must be less than 200 characters")
        private String name;
        
        @Size(max = 1000, message = "Description must be less than 1000 characters")
        private String description;
        
        @Future(message = "Valid until date must be in the future")
        private Instant validUntil;
        
        private Map<String, Object> metadata;
    }

    /**
     * Update auth token request DTO.
     */
    @Data
    @Builder
    public static class UpdateAuthTokenRequest {
        
        @Size(max = 200, message = "Token name must be less than 200 characters")
        private String name;
        
        @Size(max = 1000, message = "Description must be less than 1000 characters")
        private String description;
        
        private Instant validUntil;
        
        private Map<String, Object> metadata;
    }

    /**
     * Auth token status request DTO.
     */
    @Data
    public static class AuthTokenStatusRequest {
        
        @NotNull(message = "Action is required")
        private TokenAction action;
        
        private String reason;
        
        public enum TokenAction {
            ACTIVATE,
            DEACTIVATE,
            BLOCK,
            UNBLOCK
        }
    }

    /**
     * Token verification request DTO.
     */
    @Data
    public static class TokenVerificationRequest {
        
        @NotBlank(message = "Token value is required")
        private String tokenValue;
    }

    /**
     * Token verification response DTO.
     */
    @Data
    @Builder
    public static class TokenVerificationResponse {
        
        private boolean valid;
        private String message;
        private UUID userId;
        private String username;
        private AuthToken.TokenType tokenType;
        private Instant lastUsedAt;
        private long usageCount;
    }
}