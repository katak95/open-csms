package com.opencsms.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User DTO for API responses and requests.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

    private UUID id;
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, hyphens, and underscores")
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 255, message = "Email must be less than 255 characters")
    private String email;
    
    @Size(max = 100, message = "First name must be less than 100 characters")
    private String firstName;
    
    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;
    
    @Size(max = 200, message = "Display name must be less than 200 characters")
    private String displayName;
    
    @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Phone number contains invalid characters")
    @Size(max = 50, message = "Phone number must be less than 50 characters")
    private String phone;
    
    @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Mobile number contains invalid characters")
    @Size(max = 50, message = "Mobile number must be less than 50 characters")
    private String mobile;
    
    private boolean emailVerified;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format")
    private String language;
    
    @Size(max = 100, message = "Timezone must be less than 100 characters")
    private String timezone;
    
    private String dateFormat;
    private String timeFormat;
    private boolean notificationsEnabled;
    
    private boolean active;
    private boolean suspended;
    private String suspensionReason;
    
    private List<String> roles;
    private List<AuthTokenDto> authTokens;
    
    private Map<String, Object> metadata;
    
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Create user request DTO.
     */
    @Data
    @Builder
    public static class CreateUserRequest {
        
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, hyphens, and underscores")
        private String username;
        
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        private String email;
        
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String password;
        
        @Size(max = 100)
        private String firstName;
        
        @Size(max = 100)
        private String lastName;
        
        @Size(max = 200)
        private String displayName;
        
        @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Phone number contains invalid characters")
        private String phone;
        
        @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Mobile number contains invalid characters")
        private String mobile;
        
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format")
        private String language;
        
        private String timezone;
        private String dateFormat;
        private String timeFormat;
        private boolean notificationsEnabled = true;
        
        private List<String> roles;
    }

    /**
     * Update user request DTO.
     */
    @Data
    @Builder
    public static class UpdateUserRequest {
        
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, hyphens, and underscores")
        private String username;
        
        @Email(message = "Email format is invalid")
        private String email;
        
        @Size(max = 100)
        private String firstName;
        
        @Size(max = 100)
        private String lastName;
        
        @Size(max = 200)
        private String displayName;
        
        @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Phone number contains invalid characters")
        private String phone;
        
        @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]*$", message = "Mobile number contains invalid characters")
        private String mobile;
        
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language code format")
        private String language;
        
        private String timezone;
        private String dateFormat;
        private String timeFormat;
        private Boolean notificationsEnabled;
        
        private List<String> roles;
    }

    /**
     * Change password request DTO.
     */
    @Data
    public static class ChangePasswordRequest {
        
        @NotBlank(message = "Current password is required")
        private String currentPassword;
        
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String newPassword;
    }

    /**
     * User status update request DTO.
     */
    @Data
    public static class UserStatusRequest {
        
        @NotNull(message = "Action is required")
        private UserAction action;
        
        private String reason;
        
        @Min(value = 1, message = "Lock duration must be at least 1 minute")
        @Max(value = 1440, message = "Lock duration must be at most 1440 minutes (24 hours)")
        private Integer lockDurationMinutes;
        
        public enum UserAction {
            ACTIVATE,
            DEACTIVATE,
            SUSPEND,
            UNSUSPEND,
            LOCK,
            UNLOCK
        }
    }
}