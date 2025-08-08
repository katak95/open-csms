package com.opencsms.domain.tenant;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant entity for multi-tenant architecture.
 * Represents a completely isolated customer/organization.
 */
@Entity
@Table(name = "tenants", 
    indexes = {
        @Index(name = "idx_tenant_code", columnList = "code", unique = true),
        @Index(name = "idx_tenant_active", columnList = "active"),
        @Index(name = "idx_tenant_type", columnList = "tenant_type")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", nullable = false, length = 50)
    private TenantType tenantType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Embedded
    private TenantConfiguration configuration;

    @Embedded
    private TenantContact contact;

    @Embedded
    private TenantBilling billing;

    @ElementCollection
    @CollectionTable(
        name = "tenant_features",
        joinColumns = @JoinColumn(name = "tenant_id")
    )
    @Column(name = "feature")
    @Enumerated(EnumType.STRING)
    private Set<TenantFeature> enabledFeatures = new HashSet<>();

    @ElementCollection
    @CollectionTable(
        name = "tenant_metadata",
        joinColumns = @JoinColumn(name = "tenant_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (code == null) {
            code = generateTenantCode();
        }
        if (displayName == null) {
            displayName = name;
        }
    }

    private String generateTenantCode() {
        return "T" + System.currentTimeMillis();
    }

    public void activate() {
        this.active = true;
        this.activatedAt = Instant.now();
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    public void suspend(String reason) {
        this.active = false;
        this.suspendedAt = Instant.now();
        this.suspensionReason = reason;
    }

    public boolean hasFeature(TenantFeature feature) {
        return enabledFeatures.contains(feature);
    }

    public void enableFeature(TenantFeature feature) {
        enabledFeatures.add(feature);
    }

    public void disableFeature(TenantFeature feature) {
        enabledFeatures.remove(feature);
    }

    public boolean isInTrial() {
        return trialEndsAt != null && trialEndsAt.isAfter(Instant.now());
    }

    public enum TenantType {
        CPO,           // Charge Point Operator
        EMSP,          // E-Mobility Service Provider
        HUB,           // Hub operator
        ENTERPRISE,    // Enterprise customer
        DEMO,          // Demo tenant
        INTERNAL       // Internal use
    }

    public enum TenantFeature {
        OCPP_1_6,
        OCPP_2_0_1,
        OCPI_2_2_1,
        SMART_CHARGING,
        LOAD_BALANCING,
        ANALYTICS_ADVANCED,
        API_ACCESS,
        WEBHOOK_NOTIFICATIONS,
        CUSTOM_BRANDING,
        MULTI_CURRENCY,
        ROAMING,
        PLUG_AND_CHARGE,
        ISO_15118,
        V2G,
        DYNAMIC_PRICING
    }
}