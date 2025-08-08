package com.opencsms.domain.tariff;

import com.opencsms.domain.core.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a tariff for charging sessions.
 */
@Entity
@Table(name = "tariffs", indexes = {
    @Index(name = "idx_tariff_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_tariff_code", columnList = "code"),
    @Index(name = "idx_tariff_valid_dates", columnList = "valid_from, valid_until"),
    @Index(name = "idx_tariff_active", columnList = "active")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"tariffElements"})
public class Tariff extends BaseEntity {

    public enum TariffType {
        SIMPLE,         // Fixed price per kWh and/or per minute
        TIME_BASED,     // Different prices based on time of day
        TIERED,         // Price tiers based on energy or time consumed
        DYNAMIC         // Real-time pricing
    }

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_type", nullable = false, length = 20)
    private TariffType tariffType = TariffType.SIMPLE;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    // Simple pricing
    @Column(name = "price_per_kwh", precision = 10, scale = 4)
    private BigDecimal pricePerKwh;

    @Column(name = "price_per_minute", precision = 10, scale = 4)
    private BigDecimal pricePerMinute;

    @Column(name = "price_per_hour", precision = 10, scale = 4)
    private BigDecimal pricePerHour;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "connection_fee", precision = 10, scale = 2)
    private BigDecimal connectionFee;

    // Validity period
    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    // Time restrictions
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "days_of_week", length = 20)
    private String daysOfWeek; // Comma-separated: MON,TUE,WED...

    // Minimums and maximums
    @Column(name = "min_charging_amount_kwh", precision = 10, scale = 3)
    private BigDecimal minChargingAmountKwh;

    @Column(name = "max_charging_amount_kwh", precision = 10, scale = 3)
    private BigDecimal maxChargingAmountKwh;

    @Column(name = "min_session_duration_minutes")
    private Integer minSessionDurationMinutes;

    @Column(name = "max_session_duration_minutes")
    private Integer maxSessionDurationMinutes;

    // Power-based pricing
    @Column(name = "price_per_kw_slow", precision = 10, scale = 4)
    private BigDecimal pricePerKwSlow; // < 22kW

    @Column(name = "price_per_kw_fast", precision = 10, scale = 4)
    private BigDecimal pricePerKwFast; // 22-50kW

    @Column(name = "price_per_kw_rapid", precision = 10, scale = 4)
    private BigDecimal pricePerKwRapid; // > 50kW

    // Status and configuration
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "default_tariff", nullable = false)
    private Boolean defaultTariff = false;

    @Column(name = "public_tariff", nullable = false)
    private Boolean publicTariff = true;

    // Advanced pricing elements
    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber ASC")
    @Builder.Default
    private List<TariffElement> tariffElements = new ArrayList<>();

    // Applicable connectors/stations
    @Column(name = "applicable_stations", length = 1000)
    private String applicableStations; // JSON array of station IDs

    @Column(name = "applicable_connector_types", length = 200)
    private String applicableConnectorTypes; // JSON array

    // User restrictions
    @Column(name = "user_types", length = 100)
    private String userTypes; // JSON array: GUEST,MEMBER,PREMIUM

    @Column(name = "membership_required")
    private Boolean membershipRequired = false;

    // Billing configuration
    @Column(name = "billing_increment_seconds")
    private Integer billingIncrementSeconds = 60; // Default 1 minute

    @Column(name = "billing_increment_kwh", precision = 5, scale = 3)
    private BigDecimal billingIncrementKwh = BigDecimal.valueOf(0.001); // Default 1Wh

    // Tax information
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_included")
    private Boolean taxIncluded = true;

    // Metadata
    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "external_tariff_id", length = 100)
    private String externalTariffId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Check if tariff is currently valid
     */
    public boolean isCurrentlyValid() {
        Instant now = Instant.now();
        return active && 
               (validFrom == null || !now.isBefore(validFrom)) &&
               (validUntil == null || !now.isAfter(validUntil));
    }

    /**
     * Check if tariff applies to given day of week
     */
    public boolean appliesOnDay(DayOfWeek dayOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return true; // No restriction
        }
        return daysOfWeek.contains(dayOfWeek.name());
    }

    /**
     * Check if tariff applies at given time
     */
    public boolean appliesAtTime(LocalTime time) {
        if (startTime == null || endTime == null) {
            return true; // No time restriction
        }
        
        if (startTime.isBefore(endTime)) {
            // Same day range
            return !time.isBefore(startTime) && !time.isAfter(endTime);
        } else {
            // Overnight range
            return !time.isBefore(startTime) || !time.isAfter(endTime);
        }
    }

    /**
     * Calculate price based on power level
     */
    public BigDecimal getPricePerKwhForPower(BigDecimal powerKw) {
        if (powerKw == null) {
            return pricePerKwh;
        }
        
        if (pricePerKwSlow != null && powerKw.compareTo(BigDecimal.valueOf(22)) < 0) {
            return pricePerKwSlow;
        } else if (pricePerKwFast != null && powerKw.compareTo(BigDecimal.valueOf(50)) < 0) {
            return pricePerKwFast;
        } else if (pricePerKwRapid != null && powerKw.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return pricePerKwRapid;
        }
        
        return pricePerKwh;
    }

    /**
     * Add tariff element
     */
    public void addTariffElement(TariffElement element) {
        tariffElements.add(element);
        element.setTariff(this);
    }
}