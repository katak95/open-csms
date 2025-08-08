package com.opencsms.domain.user;

import com.opencsms.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity for multi-tenant user management.
 */
@Entity
@Table(name = "users",
    indexes = {
        @Index(name = "idx_users_tenant", columnList = "tenant_id"),
        @Index(name = "idx_users_username", columnList = "username"),
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_active", columnList = "active"),
        @Index(name = "idx_users_deleted", columnList = "deleted")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username_tenant", columnNames = {"username", "tenant_id"}),
        @UniqueConstraint(name = "uk_users_email_tenant", columnNames = {"email", "tenant_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "mobile", length = 50)
    private String mobile;

    // Authentication fields
    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verification_sent_at")
    private Instant emailVerificationSentAt;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_sent_at")
    private Instant passwordResetSentAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // User preferences
    @Column(name = "language", length = 5)
    private String language = "en";

    @Column(name = "timezone", length = 100)
    private String timezone = "UTC";

    @Column(name = "date_format", length = 50)
    private String dateFormat;

    @Column(name = "time_format", length = 50)
    private String timeFormat;

    @Column(name = "notifications_enabled")
    private boolean notificationsEnabled = true;

    // Status
    @Column(name = "active")
    private boolean active = true;

    @Column(name = "suspended")
    private boolean suspended = false;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    // Relationships
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<AuthToken> authTokens = new HashSet<>();

    // Helper methods
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return displayName != null ? displayName : username;
        }
    }

    public void recordLoginSuccess(String ipAddress) {
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ipAddress;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void recordLoginFailure() {
        this.failedLoginAttempts++;
    }

    public void lockAccount(int minutes) {
        this.lockedUntil = Instant.now().plusSeconds(minutes * 60L);
    }

    public void unlockAccount() {
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void suspend(String reason) {
        this.suspended = true;
        this.suspensionReason = reason;
    }

    public void unsuspend() {
        this.suspended = false;
        this.suspensionReason = null;
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.getCode().equals(roleCode));
    }

    public boolean hasPermission(String resource, String action) {
        return roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .anyMatch(permission -> 
                permission.getResource().equals(resource) && 
                permission.getAction().equals(action)
            );
    }
}