package com.opencsms.domain.session;

import com.opencsms.domain.core.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing meter values collected during a charging session.
 * Based on OCPP MeterValue specification.
 */
@Entity
@Table(name = "meter_values", indexes = {
    @Index(name = "idx_meter_session_id", columnList = "session_id"),
    @Index(name = "idx_meter_timestamp", columnList = "timestamp"),
    @Index(name = "idx_meter_measurand", columnList = "measurand")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MeterValue extends BaseEntity {

    /**
     * Measurand types based on OCPP specification
     */
    public enum Measurand {
        ENERGY_ACTIVE_IMPORT_REGISTER,  // Total energy imported (Wh)
        ENERGY_ACTIVE_EXPORT_REGISTER,  // Total energy exported (Wh)
        ENERGY_REACTIVE_IMPORT_REGISTER,
        ENERGY_REACTIVE_EXPORT_REGISTER,
        POWER_ACTIVE_IMPORT,            // Instantaneous power (W)
        POWER_ACTIVE_EXPORT,
        POWER_REACTIVE_IMPORT,
        POWER_REACTIVE_EXPORT,
        CURRENT_IMPORT,                 // Instantaneous current (A)
        CURRENT_EXPORT,
        VOLTAGE,                        // Instantaneous voltage (V)
        FREQUENCY,                      // Power line frequency (Hz)
        TEMPERATURE,                    // Temperature (Celsius)
        SOC,                           // State of Charge (%)
        RPM                            // Fan speed
    }

    /**
     * Value context
     */
    public enum Context {
        INTERRUPTION_BEGIN,
        INTERRUPTION_END,
        SAMPLE_CLOCK,
        SAMPLE_PERIODIC,
        TRANSACTION_BEGIN,
        TRANSACTION_END,
        TRIGGER,
        OTHER
    }

    /**
     * Value location
     */
    public enum Location {
        INLET,
        OUTLET,
        BODY,
        CABLE,
        EV,
        OTHER
    }

    /**
     * Phase values
     */
    public enum Phase {
        L1,
        L2,
        L3,
        N,
        L1_N,
        L2_N,
        L3_N,
        L1_L2,
        L2_L3,
        L3_L1
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChargingSession chargingSession;

    @Column(name = "session_id", insertable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurand", nullable = false, length = 40)
    private Measurand measurand;

    @Column(name = "value", nullable = false, precision = 15, scale = 3)
    private BigDecimal value;

    @Column(name = "unit", length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", length = 30)
    private Context context;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", length = 20)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 10)
    private Phase phase;

    // Calculated fields for convenience
    @Column(name = "energy_kwh", precision = 15, scale = 3)
    private BigDecimal energyKwh;

    @Column(name = "power_kw", precision = 10, scale = 3)
    private BigDecimal powerKw;

    @Column(name = "current_a", precision = 10, scale = 2)
    private BigDecimal currentA;

    @Column(name = "voltage_v", precision = 10, scale = 2)
    private BigDecimal voltageV;

    @Column(name = "soc_percent", precision = 5, scale = 2)
    private BigDecimal socPercent;

    @Column(name = "temperature_c", precision = 5, scale = 2)
    private BigDecimal temperatureC;

    /**
     * Convert raw value to appropriate unit
     */
    public void processValue() {
        if (measurand == null || value == null) return;

        switch (measurand) {
            case ENERGY_ACTIVE_IMPORT_REGISTER:
            case ENERGY_ACTIVE_EXPORT_REGISTER:
                // Convert Wh to kWh
                this.energyKwh = value.divide(BigDecimal.valueOf(1000), 3, BigDecimal.ROUND_HALF_UP);
                this.unit = "kWh";
                break;
            case POWER_ACTIVE_IMPORT:
            case POWER_ACTIVE_EXPORT:
                // Convert W to kW
                this.powerKw = value.divide(BigDecimal.valueOf(1000), 3, BigDecimal.ROUND_HALF_UP);
                this.unit = "kW";
                break;
            case CURRENT_IMPORT:
            case CURRENT_EXPORT:
                this.currentA = value;
                this.unit = "A";
                break;
            case VOLTAGE:
                this.voltageV = value;
                this.unit = "V";
                break;
            case SOC:
                this.socPercent = value;
                this.unit = "%";
                break;
            case TEMPERATURE:
                this.temperatureC = value;
                this.unit = "°C";
                break;
            case FREQUENCY:
                this.unit = "Hz";
                break;
            default:
                break;
        }
    }
}