package com.opencsms.service.user;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.user.User;
import com.opencsms.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user management with multi-tenant support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventService userEventService;
    private final UserValidationService userValidationService;

    /**
     * Create a new user.
     */
    @Transactional
    public User createUser(User user) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating user: {} for tenant: {}", user.getUsername(), tenantId);
        
        // Set tenant ID
        user.setTenantId(tenantId);
        
        // Validate user data
        userValidationService.validateUserCreation(user);
        
        // Check for duplicates
        if (userRepository.existsByUsernameAndTenantId(user.getUsername(), tenantId)) {
            throw new UserServiceException("Username already exists: " + user.getUsername());
        }
        
        if (userRepository.existsByEmailAndTenantId(user.getEmail(), tenantId)) {
            throw new UserServiceException("Email already exists: " + user.getEmail());
        }
        
        // Hash password if provided
        if (user.getPasswordHash() != null) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        
        // Set default values
        if (user.getDisplayName() == null) {
            user.setDisplayName(user.getFullName());
        }
        
        try {
            User savedUser = userRepository.save(user);
            
            // Record creation event
            userEventService.recordUserCreated(savedUser);
            
            log.info("Created user: {} with ID: {}", savedUser.getUsername(), savedUser.getId());
            return savedUser;
            
        } catch (DataIntegrityViolationException e) {
            log.error("Database constraint violation while creating user: {}", user.getUsername(), e);
            throw new UserServiceException("Failed to create user due to data constraint violation", e);
        }
    }

    /**
     * Update an existing user.
     */
    @Transactional
    public User updateUser(UUID userId, User userUpdates) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.debug("Updating user: {} for tenant: {}", userId, tenantId);
        
        User existingUser = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Validate updates
        userValidationService.validateUserUpdate(existingUser, userUpdates);
        
        // Check for username conflicts (if username is being changed)
        if (userUpdates.getUsername() != null && 
            !userUpdates.getUsername().equals(existingUser.getUsername())) {
            if (userRepository.existsByUsernameAndTenantId(userUpdates.getUsername(), tenantId)) {
                throw new UserServiceException("Username already exists: " + userUpdates.getUsername());
            }
            existingUser.setUsername(userUpdates.getUsername());
        }
        
        // Check for email conflicts (if email is being changed)
        if (userUpdates.getEmail() != null && 
            !userUpdates.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmailAndTenantId(userUpdates.getEmail(), tenantId)) {
                throw new UserServiceException("Email already exists: " + userUpdates.getEmail());
            }
            existingUser.setEmail(userUpdates.getEmail());
            existingUser.setEmailVerified(false); // Reset email verification on email change
        }
        
        // Update other fields
        updateUserFields(existingUser, userUpdates);
        
        try {
            User updatedUser = userRepository.save(existingUser);
            
            // Record update event
            userEventService.recordUserUpdated(updatedUser);
            
            log.info("Updated user: {}", updatedUser.getUsername());
            return updatedUser;
            
        } catch (DataIntegrityViolationException e) {
            log.error("Database constraint violation while updating user: {}", userId, e);
            throw new UserServiceException("Failed to update user due to data constraint violation", e);
        }
    }

    /**
     * Find user by ID within current tenant.
     */
    public Optional<User> findById(UUID userId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findById(userId)
            .filter(user -> tenantId.equals(user.getTenantId()) && !user.isDeleted());
    }

    /**
     * Find user by username within current tenant.
     */
    public Optional<User> findByUsername(String username) {
        String tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByUsernameAndTenantId(username, tenantId)
            .filter(user -> !user.isDeleted());
    }

    /**
     * Find user by email within current tenant.
     */
    public Optional<User> findByEmail(String email) {
        String tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByEmailAndTenantId(email, tenantId)
            .filter(user -> !user.isDeleted());
    }

    /**
     * Get all active users for current tenant.
     */
    public List<User> findAllActive() {
        String tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
    }

    /**
     * Get users by role.
     */
    public List<User> findByRole(String roleCode) {
        String tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByRoleCodeAndTenantId(roleCode, tenantId)
            .stream()
            .filter(user -> !user.isDeleted())
            .toList();
    }

    /**
     * Deactivate a user (soft delete).
     */
    @Transactional
    public void deactivateUser(UUID userId) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Deactivating user: {} for tenant: {}", userId, tenantId);
        
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setActive(false);
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        
        userRepository.save(user);
        
        // Record deactivation event
        userEventService.recordUserDeactivated(user);
        
        log.info("Deactivated user: {}", user.getUsername());
    }

    /**
     * Reactivate a user.
     */
    @Transactional
    public void reactivateUser(UUID userId) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Reactivating user: {} for tenant: {}", userId, tenantId);
        
        User user = userRepository.findById(userId)
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.setActive(true);
        user.setDeleted(false);
        user.setDeletedAt(null);
        
        userRepository.save(user);
        
        // Record reactivation event
        userEventService.recordUserReactivated(user);
        
        log.info("Reactivated user: {}", user.getUsername());
    }

    /**
     * Suspend a user with reason.
     */
    @Transactional
    public void suspendUser(UUID userId, String reason) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.suspend(reason);
        userRepository.save(user);
        
        // Record suspension event
        userEventService.recordUserSuspended(user, reason);
        
        log.info("Suspended user: {} with reason: {}", user.getUsername(), reason);
    }

    /**
     * Unsuspend a user.
     */
    @Transactional
    public void unsuspendUser(UUID userId) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.unsuspend();
        userRepository.save(user);
        
        // Record unsuspension event
        userEventService.recordUserUnsuspended(user);
        
        log.info("Unsuspended user: {}", user.getUsername());
    }

    /**
     * Lock user account.
     */
    @Transactional
    public void lockUser(UUID userId, int minutes) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.lockAccount(minutes);
        userRepository.save(user);
        
        // Record lock event
        userEventService.recordUserLocked(user, minutes);
        
        log.info("Locked user: {} for {} minutes", user.getUsername(), minutes);
    }

    /**
     * Unlock user account.
     */
    @Transactional
    public void unlockUser(UUID userId) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.unlockAccount();
        userRepository.save(user);
        
        // Record unlock event
        userEventService.recordUserUnlocked(user);
        
        log.info("Unlocked user: {}", user.getUsername());
    }

    /**
     * Change user password.
     */
    @Transactional
    public void changePassword(UUID userId, String newPassword) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Validate password strength
        userValidationService.validatePassword(newPassword);
        
        // Hash and set new password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        
        // Clear password reset token if exists
        user.setPasswordResetToken(null);
        user.setPasswordResetSentAt(null);
        
        userRepository.save(user);
        
        // Record password change event
        userEventService.recordPasswordChanged(user);
        
        log.info("Changed password for user: {}", user.getUsername());
    }

    /**
     * Record successful login.
     */
    @Transactional
    public void recordLoginSuccess(UUID userId, String ipAddress) {
        User user = findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        user.recordLoginSuccess(ipAddress);
        userRepository.save(user);
        
        // Record login event
        userEventService.recordUserLoggedIn(user, ipAddress);
        
        log.debug("Recorded successful login for user: {} from IP: {}", user.getUsername(), ipAddress);
    }

    /**
     * Record failed login attempt.
     */
    @Transactional
    public void recordLoginFailure(String usernameOrEmail) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        // Try to find user by username or email
        Optional<User> userOpt = userRepository.findByUsernameAndTenantId(usernameOrEmail, tenantId);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailAndTenantId(usernameOrEmail, tenantId);
        }
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.recordLoginFailure();
            
            // Lock account after 5 failed attempts
            if (user.getFailedLoginAttempts() >= 5) {
                user.lockAccount(15); // Lock for 15 minutes
                log.warn("Locked user account after 5 failed login attempts: {}", user.getUsername());
            }
            
            userRepository.save(user);
            
            // Record failed login event
            userEventService.recordUserLoginFailed(user);
        }
    }

    /**
     * Get user statistics for current tenant.
     */
    public UserStatistics getUserStatistics() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        long totalUsers = userRepository.countActiveUsersByTenant(tenantId);
        
        List<User> allUsers = userRepository.findByTenantIdAndActiveTrueAndDeletedFalse(tenantId);
        
        long activeUsers = allUsers.stream()
            .mapToLong(user -> user.isActive() && !user.isSuspended() && !user.isLocked() ? 1L : 0L)
            .sum();
        
        long suspendedUsers = allUsers.stream()
            .mapToLong(user -> user.isSuspended() ? 1L : 0L)
            .sum();
        
        long lockedUsers = allUsers.stream()
            .mapToLong(user -> user.isLocked() ? 1L : 0L)
            .sum();
        
        long unverifiedEmails = allUsers.stream()
            .mapToLong(user -> !user.isEmailVerified() ? 1L : 0L)
            .sum();
        
        return UserStatistics.builder()
            .totalUsers(totalUsers)
            .activeUsers(activeUsers)
            .suspendedUsers(suspendedUsers)
            .lockedUsers(lockedUsers)
            .unverifiedEmails(unverifiedEmails)
            .lastUpdateTime(Instant.now())
            .build();
    }

    /**
     * Process expired user locks.
     */
    @Transactional
    public void processExpiredLocks() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        List<User> expiredLocks = userRepository.findExpiredLocks(tenantId, Instant.now());
        
        for (User user : expiredLocks) {
            user.unlockAccount();
            userRepository.save(user);
            
            log.info("Automatically unlocked expired user account: {}", user.getUsername());
        }
    }

    private void updateUserFields(User existingUser, User updates) {
        if (updates.getFirstName() != null) {
            existingUser.setFirstName(updates.getFirstName());
        }
        if (updates.getLastName() != null) {
            existingUser.setLastName(updates.getLastName());
        }
        if (updates.getDisplayName() != null) {
            existingUser.setDisplayName(updates.getDisplayName());
        }
        if (updates.getPhone() != null) {
            existingUser.setPhone(updates.getPhone());
        }
        if (updates.getMobile() != null) {
            existingUser.setMobile(updates.getMobile());
        }
        if (updates.getLanguage() != null) {
            existingUser.setLanguage(updates.getLanguage());
        }
        if (updates.getTimezone() != null) {
            existingUser.setTimezone(updates.getTimezone());
        }
        if (updates.getDateFormat() != null) {
            existingUser.setDateFormat(updates.getDateFormat());
        }
        if (updates.getTimeFormat() != null) {
            existingUser.setTimeFormat(updates.getTimeFormat());
        }
        if (updates.isNotificationsEnabled() != existingUser.isNotificationsEnabled()) {
            existingUser.setNotificationsEnabled(updates.isNotificationsEnabled());
        }
        
        // Update display name if not explicitly set
        if (updates.getDisplayName() == null && 
            (updates.getFirstName() != null || updates.getLastName() != null)) {
            existingUser.setDisplayName(existingUser.getFullName());
        }
    }

    /**
     * User statistics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class UserStatistics {
        private long totalUsers;
        private long activeUsers;
        private long suspendedUsers;
        private long lockedUsers;
        private long unverifiedEmails;
        private Instant lastUpdateTime;
        
        public double getActivePercentage() {
            return totalUsers > 0 ? (double) activeUsers / totalUsers * 100.0 : 100.0;
        }
        
        public double getSuspendedPercentage() {
            return totalUsers > 0 ? (double) suspendedUsers / totalUsers * 100.0 : 0.0;
        }
        
        public double getLockedPercentage() {
            return totalUsers > 0 ? (double) lockedUsers / totalUsers * 100.0 : 0.0;
        }
        
        public double getUnverifiedPercentage() {
            return totalUsers > 0 ? (double) unverifiedEmails / totalUsers * 100.0 : 0.0;
        }
    }
}