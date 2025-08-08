package com.opencsms.domain.user;

import com.opencsms.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication token entity for RFID cards, mobile apps, API keys, etc.
 */
@Entity
@Table(name = "auth_tokens",
    indexes = {
        @Index(name = "idx_auth_tokens_tenant", columnList = "tenant_id"),
        @Index(name = "idx_auth_tokens_user", columnList = "user_id"),
        @Index(name = "idx_auth_tokens_type", columnList = "token_type"),
        @Index(name = "idx_auth_tokens_value", columnList = "token_value"),
        @Index(name = "idx_auth_tokens_active", columnList = "active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_tokens_value_tenant", columnNames = {"token_value", "tenant_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_auth_tokens_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 50)
    private TokenType tokenType;

    @Column(name = "token_value", nullable = false, length = 255)
    private String tokenValue;

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Validity
    @Column(name = "valid_from")
    private Instant validFrom = Instant.now();

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "usage_count")
    private long usageCount = 0L;

    // Status
    @Column(name = "active")
    private boolean active = true;

    @Column(name = "blocked")
    private boolean blocked = false;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // Helper methods
    public boolean isValid() {
        Instant now = Instant.now();
        return active && 
               !blocked && 
               !isDeleted() && 
               (validFrom == null || !validFrom.isAfter(now)) &&
               (validUntil == null || !validUntil.isBefore(now));
    }

    public void recordUsage() {
        this.lastUsedAt = Instant.now();
        this.usageCount++;
    }

    public void block(String reason) {
        this.blocked = true;
        this.blockReason = reason;
    }

    public void unblock() {
        this.blocked = false;
        this.blockReason = null;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
        this.blocked = false;
        this.blockReason = null;
    }

    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(Instant.now());
    }

    public boolean isNotYetValid() {
        return validFrom != null && validFrom.isAfter(Instant.now());
    }

    public enum TokenType {
        RFID,           // RFID card
        NFC,            // NFC card/tag
        MOBILE_APP,     // Mobile application token
        API_KEY,        // API access key
        CREDIT_CARD,    // Credit card token
        BARCODE,        // Barcode/QR code
        BIOMETRIC,      // Biometric identifier
        VEHICLE_ID,     // Vehicle identification
        CUSTOM          // Custom token type
    }
}