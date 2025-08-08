package com.opencsms.web.controller;

import com.opencsms.domain.user.User;
import com.opencsms.service.user.UserService;
import com.opencsms.web.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
 * REST controller for user management.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserController {

    private final UserService userService;

    /**
     * Get all users with pagination.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<Page<UserDto>> getUsers(Pageable pageable) {
        log.debug("Getting users with pagination: {}", pageable);
        
        List<User> users = userService.findAllActive();
        List<UserDto> userDtos = users.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        // Simple pagination implementation (in real scenarios, use repository pagination)
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), userDtos.size());
        List<UserDto> pageContent = userDtos.subList(start, Math.min(end, userDtos.size()));
        
        Page<UserDto> page = new PageImpl<>(pageContent, pageable, userDtos.size());
        
        return ResponseEntity.ok(page);
    }

    /**
     * Get user by ID.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:READ') or #userId == authentication.principal.id")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID userId) {
        log.debug("Getting user by ID: {}", userId);
        
        return userService.findById(userId)
            .map(user -> ResponseEntity.ok(convertToDto(user)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get current user profile.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
        String username = request.getUserPrincipal().getName();
        log.debug("Getting current user profile: {}", username);
        
        return userService.findByUsername(username)
            .map(user -> ResponseEntity.ok(convertToDto(user)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new user.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('USER:CREATE')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto.CreateUserRequest request) {
        log.info("Creating new user: {}", request.getUsername());
        
        User user = convertFromCreateRequest(request);
        User createdUser = userService.createUser(user);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(convertToDto(createdUser));
    }

    /**
     * Update user.
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER:UPDATE') or #userId == authentication.principal.id")
    public ResponseEntity<UserDto> updateUser(@PathVariable UUID userId,
                                            @Valid @RequestBody UserDto.UpdateUserRequest request) {
        log.debug("Updating user: {}", userId);
        
        User updates = convertFromUpdateRequest(request);
        User updatedUser = userService.updateUser(userId, updates);
        
        return ResponseEntity.ok(convertToDto(updatedUser));
    }

    /**
     * Update current user profile.
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> updateCurrentUser(@Valid @RequestBody UserDto.UpdateUserRequest request,
                                                   HttpServletRequest servletRequest) {
        String username = servletRequest.getUserPrincipal().getName();
        log.debug("Updating current user profile: {}", username);
        
        User currentUser = userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
        
        User updates = convertFromUpdateRequest(request);
        User updatedUser = userService.updateUser(currentUser.getId(), updates);
        
        return ResponseEntity.ok(convertToDto(updatedUser));
    }

    /**
     * Change user password.
     */
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("hasAuthority('USER:UPDATE') or #userId == authentication.principal.id")
    public ResponseEntity<Void> changePassword(@PathVariable UUID userId,
                                             @Valid @RequestBody UserDto.ChangePasswordRequest request,
                                             HttpServletRequest servletRequest) {
        log.debug("Changing password for user: {}", userId);
        
        // For security, users can only change their own password using current password
        if (userId.equals(getCurrentUserId(servletRequest))) {
            // TODO: Verify current password before changing
            userService.changePassword(userId, request.getNewPassword());
        } else {
            // Admin can change password without current password verification
            userService.changePassword(userId, request.getNewPassword());
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * Update user status (activate, deactivate, suspend, etc.).
     */
    @PostMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public ResponseEntity<Void> updateUserStatus(@PathVariable UUID userId,
                                               @Valid @RequestBody UserDto.UserStatusRequest request) {
        log.debug("Updating user status: {} - {}", userId, request.getAction());
        
        switch (request.getAction()) {
            case ACTIVATE:
                userService.reactivateUser(userId);
                break;
            case DEACTIVATE:
                userService.deactivateUser(userId);
                break;
            case SUSPEND:
                userService.suspendUser(userId, request.getReason());
                break;
            case UNSUSPEND:
                userService.unsuspendUser(userId);
                break;
            case LOCK:
                int lockDuration = request.getLockDurationMinutes() != null ? 
                    request.getLockDurationMinutes() : 15; // Default 15 minutes
                userService.lockUser(userId, lockDuration);
                break;
            case UNLOCK:
                userService.unlockUser(userId);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * Get users by role.
     */
    @GetMapping("/by-role/{roleCode}")
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<List<UserDto>> getUsersByRole(@PathVariable String roleCode) {
        log.debug("Getting users by role: {}", roleCode);
        
        List<User> users = userService.findByRole(roleCode);
        List<UserDto> userDtos = users.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(userDtos);
    }

    /**
     * Get user statistics.
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<UserService.UserStatistics> getUserStatistics() {
        log.debug("Getting user statistics");
        
        UserService.UserStatistics statistics = userService.getUserStatistics();
        return ResponseEntity.ok(statistics);
    }

    /**
     * Process expired locks (admin utility endpoint).
     */
    @PostMapping("/process-expired-locks")
    @PreAuthorize("hasAuthority('USER:ADMIN')")
    public ResponseEntity<Void> processExpiredLocks() {
        log.debug("Processing expired user locks");
        
        userService.processExpiredLocks();
        return ResponseEntity.ok().build();
    }

    // Helper methods for DTO conversion
    private UserDto convertToDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .displayName(user.getDisplayName())
            .phone(user.getPhone())
            .mobile(user.getMobile())
            .emailVerified(user.isEmailVerified())
            .lastLoginAt(user.getLastLoginAt())
            .lastLoginIp(user.getLastLoginIp())
            .failedLoginAttempts(user.getFailedLoginAttempts())
            .lockedUntil(user.getLockedUntil())
            .language(user.getLanguage())
            .timezone(user.getTimezone())
            .dateFormat(user.getDateFormat())
            .timeFormat(user.getTimeFormat())
            .notificationsEnabled(user.isNotificationsEnabled())
            .active(user.isActive())
            .suspended(user.isSuspended())
            .suspensionReason(user.getSuspensionReason())
            .roles(user.getRoles().stream()
                .map(role -> role.getCode())
                .collect(Collectors.toList()))
            .metadata(user.getMetadata())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }

    private User convertFromCreateRequest(UserDto.CreateUserRequest request) {
        return User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(request.getPassword()) // Will be encoded by UserService
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .displayName(request.getDisplayName())
            .phone(request.getPhone())
            .mobile(request.getMobile())
            .language(request.getLanguage())
            .timezone(request.getTimezone())
            .dateFormat(request.getDateFormat())
            .timeFormat(request.getTimeFormat())
            .notificationsEnabled(request.isNotificationsEnabled())
            .active(true)
            .build();
    }

    private User convertFromUpdateRequest(UserDto.UpdateUserRequest request) {
        return User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .displayName(request.getDisplayName())
            .phone(request.getPhone())
            .mobile(request.getMobile())
            .language(request.getLanguage())
            .timezone(request.getTimezone())
            .dateFormat(request.getDateFormat())
            .timeFormat(request.getTimeFormat())
            .notificationsEnabled(request.getNotificationsEnabled())
            .build();
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        String username = request.getUserPrincipal().getName();
        return userService.findByUsername(username)
            .map(User::getId)
            .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}