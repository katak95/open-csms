package com.opencsms.domain.tariff;

import com.opencsms.domain.core.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entity representing a pricing element within a tariff structure.
 * Supports complex pricing models like time-based, tiered, and step pricing.
 */
@Entity
@Table(name = "tariff_elements", indexes = {
    @Index(name = "idx_element_tariff_id", columnList = "tariff_id"),
    @Index(name = "idx_element_sequence", columnList = "sequence_number")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TariffElement extends BaseEntity {

    public enum PriceComponent {
        ENERGY,         // Price per kWh
        TIME,           // Price per hour/minute
        FLAT,           // Flat fee
        PARKING_TIME,   // Parking fee after charging
        RESERVATION,    // Reservation fee
        TRANSACTION     // Transaction fee
    }

    public enum StepSize {
        SECOND(1),
        MINUTE(60),
        HOUR(3600),
        DAY(86400),
        KWH(1),
        WH(1000);
        
        private final int factor;
        StepSize(int factor) { this.factor = factor; }
        public int getFactor() { return factor; }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id", nullable = false)
    private Tariff tariff;

    @Column(name = "tariff_id", insertable = false, updatable = false)
    private UUID tariffId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_component", nullable = false, length = 20)
    private PriceComponent priceComponent;

    @Column(name = "price", nullable = false, precision = 10, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_size", length = 20)
    private StepSize stepSize;

    // Thresholds for tiered pricing
    @Column(name = "min_value", precision = 15, scale = 3)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 15, scale = 3)
    private BigDecimal maxValue;

    // Time restrictions
    @Column(name = "start_time", length = 8)
    private String startTime; // HH:MM:SS

    @Column(name = "end_time", length = 8)
    private String endTime; // HH:MM:SS

    @Column(name = "min_duration_seconds")
    private Integer minDurationSeconds;

    @Column(name = "max_duration_seconds")
    private Integer maxDurationSeconds;

    // Days of week (bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64)
    @Column(name = "days_mask")
    private Integer daysMask = 127; // Default: all days

    // Additional configuration
    @Column(name = "billing_increment", precision = 10, scale = 3)
    private BigDecimal billingIncrement;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Check if element applies on given day (1=Monday, 7=Sunday)
     */
    public boolean appliesOnDay(int dayOfWeek) {
        if (daysMask == null) return true;
        int dayBit = 1 << (dayOfWeek - 1);
        return (daysMask & dayBit) != 0;
    }

    /**
     * Check if value falls within this element's range
     */
    public boolean appliesForValue(BigDecimal value) {
        if (value == null) return true;
        
        boolean minCheck = minValue == null || value.compareTo(minValue) >= 0;
        boolean maxCheck = maxValue == null || value.compareTo(maxValue) < 0;
        
        return minCheck && maxCheck;
    }
}