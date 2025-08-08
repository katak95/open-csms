package com.opencsms.domain.station;

import com.opencsms.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Connector entity representing a physical charging connector on a station.
 */
@Entity
@Table(name = "connectors",
    indexes = {
        @Index(name = "idx_connectors_tenant", columnList = "tenant_id"),
        @Index(name = "idx_connectors_station", columnList = "charging_station_id"),
        @Index(name = "idx_connectors_connector_id", columnList = "connector_id"),
        @Index(name = "idx_connectors_status", columnList = "status"),
        @Index(name = "idx_connectors_type", columnList = "connector_type"),
        @Index(name = "idx_connectors_standard", columnList = "standard")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_connectors_station_connector", 
                         columnNames = {"charging_station_id", "connector_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Connector extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charging_station_id", nullable = false,
               foreignKey = @ForeignKey(name = "fk_connectors_station"))
    private ChargingStation chargingStation;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ConnectorStatus status = ConnectorStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 50)
    private ConnectorErrorCode errorCode;

    @Column(name = "error_info", length = 500)
    private String errorInfo;

    // Physical characteristics
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 50)
    private ConnectorType connectorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "standard", nullable = false, length = 50)
    private ConnectorStandard standard;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 50)
    private ConnectorFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "power_type", nullable = false, length = 20)
    private PowerType powerType;

    // Power specifications
    @Column(name = "max_voltage", precision = 8, scale = 2)
    private BigDecimal maxVoltage;

    @Column(name = "max_amperage", precision = 8, scale = 2)
    private BigDecimal maxAmperage;

    @Column(name = "max_electric_power", precision = 8, scale = 2)
    private BigDecimal maxElectricPower;

    @Column(name = "rated_power_kw", precision = 8, scale = 2)
    private BigDecimal ratedPowerKw;

    // Current status information
    @Column(name = "current_transaction_id")
    private Integer currentTransactionId;

    @Column(name = "current_user_id", length = 255)
    private String currentUserId;

    @Column(name = "current_id_tag", length = 255)
    private String currentIdTag;

    @Column(name = "current_charging_power_kw", precision = 8, scale = 3)
    private BigDecimal currentChargingPowerKw = BigDecimal.ZERO;

    @Column(name = "current_energy_kwh", precision = 12, scale = 3)
    private BigDecimal currentEnergyKwh = BigDecimal.ZERO;

    @Column(name = "current_session_start")
    private Instant currentSessionStart;

    // Metering
    @Column(name = "meter_serial_number", length = 100)
    private String meterSerialNumber;

    @Column(name = "meter_type", length = 50)
    private String meterType;

    @Column(name = "total_energy_reading_kwh", precision = 12, scale = 3)
    private BigDecimal totalEnergyReadingKwh = BigDecimal.ZERO;

    @Column(name = "last_meter_reading_kwh", precision = 12, scale = 3)
    private BigDecimal lastMeterReadingKwh = BigDecimal.ZERO;

    @Column(name = "last_meter_reading_timestamp")
    private Instant lastMeterReadingTimestamp;

    // Tariff and pricing
    @Column(name = "tariff_id", length = 100)
    private String tariffId;

    @Column(name = "price_per_kwh", precision = 8, scale = 4)
    private BigDecimal pricePerKwh;

    @Column(name = "price_per_minute", precision = 8, scale = 4)
    private BigDecimal pricePerMinute;

    @Column(name = "price_per_session", precision = 8, scale = 2)
    private BigDecimal pricePerSession;

    // Reservations
    @Column(name = "reserved_id_tag", length = 255)
    private String reservedIdTag;

    @Column(name = "reservation_id")
    private Integer reservationId;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    // Smart charging
    @Column(name = "charge_point_max_profile")
    private Integer chargePointMaxProfile;

    @Column(name = "max_charging_profiles_installed")
    private Integer maxChargingProfilesInstalled = 10;

    @Column(name = "charging_rate_unit_supported")
    private String chargingRateUnitSupported = "W,A";

    // Physical access
    @Column(name = "physical_reference", length = 100)
    private String physicalReference;

    @Column(name = "floor_level", length = 10)
    private String floorLevel;

    @Column(name = "parking_restrictions")
    private String parkingRestrictions;

    @Column(name = "accessible_disabled")
    private boolean accessibleDisabled = false;

    // Terms and conditions
    @Column(name = "terms_and_conditions", length = 500)
    private String termsAndConditions;

    // Last status update
    @Column(name = "last_status_notification")
    private Instant lastStatusNotification;

    @Column(name = "last_status_change")
    private Instant lastStatusChange;

    // Maintenance
    @Column(name = "maintenance_mode")
    private boolean maintenanceMode = false;

    @Column(name = "maintenance_reason", length = 500)
    private String maintenanceReason;

    // Statistics
    @Column(name = "total_sessions")
    private Long totalSessions = 0L;

    @Column(name = "total_energy_delivered_kwh", precision = 12, scale = 3)
    private BigDecimal totalEnergyDeliveredKwh = BigDecimal.ZERO;

    @Column(name = "total_revenue", precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "avg_session_duration_minutes")
    private Integer avgSessionDurationMinutes = 0;

    @Column(name = "uptime_percentage", precision = 5, scale = 2)
    private BigDecimal uptimePercentage = BigDecimal.valueOf(100.0);

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // Helper methods
    public void updateStatus(ConnectorStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.lastStatusChange = Instant.now();
            this.lastStatusNotification = Instant.now();
        }
    }

    public void updateStatus(ConnectorStatus newStatus, ConnectorErrorCode errorCode, String errorInfo) {
        updateStatus(newStatus);
        this.errorCode = errorCode;
        this.errorInfo = errorInfo;
    }

    public void clearError() {
        this.errorCode = null;
        this.errorInfo = null;
    }

    public void startTransaction(Integer transactionId, String idTag, String userId) {
        this.currentTransactionId = transactionId;
        this.currentIdTag = idTag;
        this.currentUserId = userId;
        this.currentSessionStart = Instant.now();
        updateStatus(ConnectorStatus.OCCUPIED);
    }

    public void stopTransaction() {
        this.currentTransactionId = null;
        this.currentIdTag = null;
        this.currentUserId = null;
        this.currentChargingPowerKw = BigDecimal.ZERO;
        this.currentEnergyKwh = BigDecimal.ZERO;
        this.currentSessionStart = null;
        updateStatus(ConnectorStatus.AVAILABLE);
    }

    public void reserve(Integer reservationId, String idTag, Instant expiresAt) {
        this.reservationId = reservationId;
        this.reservedIdTag = idTag;
        this.reservationExpiresAt = expiresAt;
        updateStatus(ConnectorStatus.RESERVED);
    }

    public void cancelReservation() {
        this.reservationId = null;
        this.reservedIdTag = null;
        this.reservationExpiresAt = null;
        updateStatus(ConnectorStatus.AVAILABLE);
    }

    public boolean isReserved() {
        return reservationId != null && 
               reservationExpiresAt != null && 
               reservationExpiresAt.isAfter(Instant.now());
    }

    public boolean isReservedFor(String idTag) {
        return isReserved() && 
               reservedIdTag != null && 
               reservedIdTag.equals(idTag);
    }

    public void enterMaintenanceMode(String reason) {
        this.maintenanceMode = true;
        this.maintenanceReason = reason;
        updateStatus(ConnectorStatus.UNAVAILABLE);
    }

    public void exitMaintenanceMode() {
        this.maintenanceMode = false;
        this.maintenanceReason = null;
        updateStatus(ConnectorStatus.AVAILABLE);
    }

    public void updateMeterReading(BigDecimal energyKwh) {
        this.lastMeterReadingKwh = energyKwh;
        this.lastMeterReadingTimestamp = Instant.now();
        
        if (currentTransactionId != null) {
            // Calculate energy for current session
            BigDecimal sessionStartEnergy = totalEnergyReadingKwh.subtract(currentEnergyKwh);
            this.currentEnergyKwh = energyKwh.subtract(sessionStartEnergy);
        }
    }

    public boolean isAvailable() {
        return status == ConnectorStatus.AVAILABLE && 
               !maintenanceMode && 
               !isDeleted();
    }

    public boolean isOperational() {
        return !maintenanceMode && 
               status != ConnectorStatus.FAULTED && 
               status != ConnectorStatus.UNAVAILABLE &&
               !isDeleted();
    }

    public String getDisplayName() {
        return chargingStation.getName() + " - Connector " + connectorId;
    }

    public enum ConnectorStatus {
        AVAILABLE,      // Available for charging
        OCCUPIED,       // Occupied by a vehicle
        RESERVED,       // Reserved for a specific user
        UNAVAILABLE,    // Not available (maintenance, error, etc.)
        FAULTED         // Has a fault
    }

    public enum ConnectorErrorCode {
        CONNECTOR_LOCK_FAILURE,
        EV_COMMUNICATION_ERROR,
        GROUND_FAILURE,
        HIGH_TEMPERATURE,
        INTERNAL_ERROR,
        LOCAL_LIST_CONFLICT,
        NO_ERROR,
        OTHER_ERROR,
        OVER_CURRENT_FAILURE,
        OVER_VOLTAGE,
        POWER_METER_FAILURE,
        POWER_SWITCH_FAILURE,
        READER_FAILURE,
        RESET_FAILURE,
        UNDER_VOLTAGE,
        WEAK_SIGNAL
    }

    public enum ConnectorType {
        CHADEMO,
        DOMESTIC_A,
        DOMESTIC_B,
        DOMESTIC_C,
        DOMESTIC_D,
        DOMESTIC_E,
        DOMESTIC_F,
        DOMESTIC_G,
        DOMESTIC_H,
        DOMESTIC_I,
        DOMESTIC_J,
        DOMESTIC_K,
        DOMESTIC_L,
        IEC_60309_2_single_16,
        IEC_60309_2_three_16,
        IEC_60309_2_three_32,
        IEC_60309_2_three_64,
        IEC_62196_T1,
        IEC_62196_T1_COMBO,
        IEC_62196_T2,
        IEC_62196_T2_COMBO,
        IEC_62196_T3A,
        IEC_62196_T3C,
        PANTOGRAPH_BOTTOM_UP,
        PANTOGRAPH_TOP_DOWN,
        TESLA_R,
        TESLA_S
    }

    public enum ConnectorStandard {
        IEC_62196_T1,
        IEC_62196_T2,
        IEC_62196_T3,
        IEC_61851_1,
        CHADEMO,
        IEC_62196_T1_COMBO,
        IEC_62196_T2_COMBO,
        TESLA_CONNECTOR,
        TESLA_SUPERCHARGER
    }

    public enum ConnectorFormat {
        SOCKET,
        CABLE
    }

    public enum PowerType {
        AC_1_PHASE,
        AC_3_PHASE,
        DC
    }
}