package com.opencsms.domain.session;

import com.opencsms.domain.core.BaseEntity;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.user.User;
import com.opencsms.domain.user.AuthToken;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a charging session for an electric vehicle.
 * Manages the complete lifecycle of a charging transaction with state machine.
 */
@Entity
@Table(name = "charging_sessions", indexes = {
    @Index(name = "idx_session_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_session_user_id", columnList = "user_id"),
    @Index(name = "idx_session_station_id", columnList = "station_id"),
    @Index(name = "idx_session_connector_id", columnList = "connector_id"),
    @Index(name = "idx_session_status", columnList = "status"),
    @Index(name = "idx_session_start_time", columnList = "start_time"),
    @Index(name = "idx_session_transaction_id", columnList = "ocpp_transaction_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"meterValues", "statusHistory"})
public class ChargingSession extends BaseEntity {

    /**
     * Session status enumeration with state machine transitions
     */
    public enum SessionStatus {
        PENDING,        // Session created but not started
        AUTHORIZING,    // Waiting for authorization
        AUTHORIZED,     // Authorized but not started
        STARTING,       // Starting transaction
        CHARGING,       // Active charging
        SUSPENDED_EV,   // Suspended by EV
        SUSPENDED_EVSE, // Suspended by EVSE
        FINISHING,      // Stopping transaction
        COMPLETED,      // Successfully completed
        FAILED,         // Failed to start or error
        CANCELLED       // Cancelled by user or system
    }

    /**
     * Stop reason enumeration based on OCPP specification
     */
    public enum StopReason {
        EMERGENCY_STOP,
        EV_DISCONNECTED,
        HARD_RESET,
        LOCAL,
        OTHER,
        POWER_LOSS,
        REBOOT,
        REMOTE,
        SOFT_RESET,
        UNLOCK_COMMAND,
        DE_AUTHORIZED,
        SESSION_TIMEOUT,
        TRANSACTION_LIMIT
    }

    @Column(name = "session_uuid", nullable = false, unique = true)
    private UUID sessionUuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_token_id")
    private AuthToken authToken;

    @Column(name = "auth_token_id", insertable = false, updatable = false)
    private UUID authTokenId;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "station_serial", length = 100)
    private String stationSerial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @Column(name = "connector_id", insertable = false, updatable = false, nullable = false)
    private UUID connectorId;

    @Column(name = "connector_number", nullable = false)
    private Integer connectorNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING;

    @Column(name = "ocpp_transaction_id")
    private Integer ocppTransactionId;

    @Column(name = "ocpp_id_tag", length = 50)
    private String ocppIdTag;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "authorization_time")
    private Instant authorizationTime;

    @Column(name = "meter_start", precision = 15, scale = 3)
    private BigDecimal meterStart;

    @Column(name = "meter_stop", precision = 15, scale = 3)
    private BigDecimal meterStop;

    @Column(name = "energy_delivered_kwh", precision = 15, scale = 3)
    private BigDecimal energyDeliveredKwh;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_power_kw", precision = 10, scale = 3)
    private BigDecimal maxPowerKw;

    @Column(name = "average_power_kw", precision = 10, scale = 3)
    private BigDecimal averagePowerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 30)
    private StopReason stopReason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Pricing and billing
    @Column(name = "tariff_id")
    private UUID tariffId;

    @Column(name = "currency", length = 3)
    private String currency = "EUR";

    @Column(name = "price_per_kwh", precision = 10, scale = 4)
    private BigDecimal pricePerKwh;

    @Column(name = "price_per_minute", precision = 10, scale = 4)
    private BigDecimal pricePerMinute;

    @Column(name = "session_cost", precision = 10, scale = 2)
    private BigDecimal sessionCost;

    @Column(name = "energy_cost", precision = 10, scale = 2)
    private BigDecimal energyCost;

    @Column(name = "time_cost", precision = 10, scale = 2)
    private BigDecimal timeCost;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;

    // Vehicle information
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    // Meter values collection
    @OneToMany(mappedBy = "chargingSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<MeterValue> meterValues = new ArrayList<>();

    // Status history tracking
    @OneToMany(mappedBy = "chargingSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<SessionStatusHistory> statusHistory = new ArrayList<>();

    // Remote control
    @Column(name = "remote_start")
    private Boolean remoteStart = false;

    @Column(name = "remote_stop")
    private Boolean remoteStop = false;

    @Column(name = "remote_stop_requested")
    private Instant remoteStopRequested;

    // Reservation link
    @Column(name = "reservation_id")
    private UUID reservationId;

    // OCPI session
    @Column(name = "ocpi_session_id", length = 36)
    private String ocpiSessionId;

    @Column(name = "roaming_session")
    private Boolean roamingSession = false;

    @Column(name = "partner_name", length = 100)
    private String partnerName;

    // Additional metadata
    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "charging_profile_id")
    private UUID chargingProfileId;

    @Column(name = "max_charging_rate_kw", precision = 10, scale = 3)
    private BigDecimal maxChargingRateKw;

    /**
     * Calculate session duration in minutes
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            this.durationMinutes = (int) duration.toMinutes();
        }
    }

    /**
     * Calculate energy delivered
     */
    public void calculateEnergyDelivered() {
        if (meterStop != null && meterStart != null) {
            this.energyDeliveredKwh = meterStop.subtract(meterStart).divide(BigDecimal.valueOf(1000));
        }
    }

    /**
     * Calculate session costs based on tariff
     */
    public void calculateCosts() {
        if (energyDeliveredKwh != null && pricePerKwh != null) {
            this.energyCost = energyDeliveredKwh.multiply(pricePerKwh);
        } else {
            this.energyCost = BigDecimal.ZERO;
        }

        if (durationMinutes != null && pricePerMinute != null) {
            this.timeCost = BigDecimal.valueOf(durationMinutes).multiply(pricePerMinute);
        } else {
            this.timeCost = BigDecimal.ZERO;
        }

        this.sessionCost = energyCost.add(timeCost);
        
        if (serviceFee == null) {
            serviceFee = BigDecimal.ZERO;
        }
        
        this.totalCost = sessionCost.add(serviceFee);
    }

    /**
     * Check if session is active
     */
    public boolean isActive() {
        return status == SessionStatus.CHARGING || 
               status == SessionStatus.SUSPENDED_EV || 
               status == SessionStatus.SUSPENDED_EVSE;
    }

    /**
     * Check if session can be stopped
     */
    public boolean canBeStopped() {
        return isActive() || status == SessionStatus.STARTING;
    }

    /**
     * Check if session is completed
     */
    public boolean isCompleted() {
        return status == SessionStatus.COMPLETED || 
               status == SessionStatus.FAILED || 
               status == SessionStatus.CANCELLED;
    }

    /**
     * Add meter value to session
     */
    public void addMeterValue(MeterValue meterValue) {
        meterValues.add(meterValue);
        meterValue.setChargingSession(this);
        
        // Update max and average power
        if (meterValue.getPowerKw() != null) {
            if (maxPowerKw == null || meterValue.getPowerKw().compareTo(maxPowerKw) > 0) {
                maxPowerKw = meterValue.getPowerKw();
            }
        }
    }

    /**
     * Add status history entry
     */
    public void addStatusHistory(SessionStatus newStatus, String reason) {
        SessionStatusHistory history = SessionStatusHistory.builder()
            .chargingSession(this)
            .fromStatus(this.status)
            .toStatus(newStatus)
            .timestamp(Instant.now())
            .reason(reason)
            .build();
        statusHistory.add(history);
        this.status = newStatus;
    }

    /**
     * Transition to new status with validation
     */
    public boolean transitionTo(SessionStatus newStatus, String reason) {
        if (canTransitionTo(newStatus)) {
            addStatusHistory(newStatus, reason);
            return true;
        }
        return false;
    }

    /**
     * Check if transition to new status is valid
     */
    public boolean canTransitionTo(SessionStatus newStatus) {
        switch (status) {
            case PENDING:
                return newStatus == SessionStatus.AUTHORIZING || 
                       newStatus == SessionStatus.FAILED ||
                       newStatus == SessionStatus.CANCELLED;
            case AUTHORIZING:
                return newStatus == SessionStatus.AUTHORIZED || 
                       newStatus == SessionStatus.FAILED ||
                       newStatus == SessionStatus.CANCELLED;
            case AUTHORIZED:
                return newStatus == SessionStatus.STARTING || 
                       newStatus == SessionStatus.FAILED ||
                       newStatus == SessionStatus.CANCELLED;
            case STARTING:
                return newStatus == SessionStatus.CHARGING || 
                       newStatus == SessionStatus.FAILED ||
                       newStatus == SessionStatus.CANCELLED;
            case CHARGING:
                return newStatus == SessionStatus.SUSPENDED_EV ||
                       newStatus == SessionStatus.SUSPENDED_EVSE ||
                       newStatus == SessionStatus.FINISHING ||
                       newStatus == SessionStatus.COMPLETED ||
                       newStatus == SessionStatus.FAILED;
            case SUSPENDED_EV:
            case SUSPENDED_EVSE:
                return newStatus == SessionStatus.CHARGING ||
                       newStatus == SessionStatus.FINISHING ||
                       newStatus == SessionStatus.COMPLETED ||
                       newStatus == SessionStatus.FAILED;
            case FINISHING:
                return newStatus == SessionStatus.COMPLETED ||
                       newStatus == SessionStatus.FAILED;
            default:
                return false;
        }
    }
}