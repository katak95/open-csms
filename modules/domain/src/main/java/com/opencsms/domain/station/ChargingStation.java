package com.opencsms.domain.station;

import com.opencsms.domain.base.BaseEntity;
import com.opencsms.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Charging Station entity representing a physical charging station.
 */
@Entity
@Table(name = "charging_stations",
    indexes = {
        @Index(name = "idx_stations_tenant", columnList = "tenant_id"),
        @Index(name = "idx_stations_station_id", columnList = "station_id"),
        @Index(name = "idx_stations_status", columnList = "status"),
        @Index(name = "idx_stations_location", columnList = "latitude, longitude"),
        @Index(name = "idx_stations_network", columnList = "network_operator"),
        @Index(name = "idx_stations_active", columnList = "active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stations_station_id_tenant", columnNames = {"station_id", "tenant_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChargingStation extends BaseEntity {

    @Column(name = "station_id", nullable = false, length = 100)
    private String stationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private StationStatus status = StationStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 50)
    private StationAvailability availability = StationAvailability.OPERATIVE;

    // Location information
    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "altitude", precision = 8, scale = 3)
    private BigDecimal altitude;

    // Network and operator information
    @Column(name = "network_operator", length = 100)
    private String networkOperator;

    @Column(name = "operator_name", length = 200)
    private String operatorName;

    @Column(name = "network_id", length = 100)
    private String networkId;

    // Technical specifications
    @Column(name = "vendor", length = 100)
    private String vendor;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "ocpp_version", length = 10)
    private String ocppVersion;

    @Column(name = "max_power_kw", precision = 8, scale = 2)
    private BigDecimal maxPowerKw;

    @Column(name = "num_connectors")
    private Integer numConnectors;

    // Communication settings
    @Column(name = "websocket_url", length = 500)
    private String websocketUrl;

    @Column(name = "heartbeat_interval")
    private Integer heartbeatInterval = 300; // seconds

    @Column(name = "meter_values_sample_interval")
    private Integer meterValuesSampleInterval = 60; // seconds

    @Column(name = "clock_aligned_data_interval")
    private Integer clockAlignedDataInterval = 900; // seconds

    // Connection status
    @Column(name = "connected")
    private boolean connected = false;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "last_boot_notification")
    private Instant lastBootNotification;

    @Column(name = "connection_timeout")
    private Integer connectionTimeout = 60; // seconds

    // Configuration
    @Column(name = "local_authorize_offline")
    private boolean localAuthorizeOffline = true;

    @Column(name = "local_pre_authorize")
    private boolean localPreAuthorize = false;

    @Column(name = "allow_offline_tx_for_unknown_id")
    private boolean allowOfflineTxForUnknownId = false;

    @Column(name = "authorization_cache_enabled")
    private boolean authorizationCacheEnabled = true;

    @Column(name = "stop_transaction_on_ev_side_disconnect")
    private boolean stopTransactionOnEvSideDisconnect = true;

    @Column(name = "stop_transaction_on_invalid_id")
    private boolean stopTransactionOnInvalidId = true;

    @Column(name = "unlock_connector_on_ev_side_disconnect")
    private boolean unlockConnectorOnEvSideDisconnect = true;

    // Smart charging
    @Column(name = "smart_charging_enabled")
    private boolean smartChargingEnabled = false;

    @Column(name = "charge_profile_max_stack_level")
    private Integer chargeProfileMaxStackLevel = 10;

    @Column(name = "charging_schedule_allowed_charging_rate_unit")
    private String chargingScheduleAllowedChargingRateUnit = "W,A";

    @Column(name = "charging_schedule_max_periods")
    private Integer chargingScheduleMaxPeriods = 24;

    // Active/inactive
    @Column(name = "active")
    private boolean active = true;

    @Column(name = "maintenance_mode")
    private boolean maintenanceMode = false;

    @Column(name = "maintenance_reason", length = 500)
    private String maintenanceReason;

    // Relationships
    @OneToMany(mappedBy = "chargingStation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Connector> connectors = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(name = "fk_stations_owner"))
    private User owner;

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // Statistics (read-only, updated by triggers/services)
    @Column(name = "total_energy_delivered_kwh", precision = 12, scale = 3)
    private BigDecimal totalEnergyDeliveredKwh = BigDecimal.ZERO;

    @Column(name = "total_sessions")
    private Long totalSessions = 0L;

    @Column(name = "total_revenue", precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "avg_session_duration_minutes")
    private Integer avgSessionDurationMinutes = 0;

    @Column(name = "last_session_start")
    private Instant lastSessionStart;

    @Column(name = "last_session_end")
    private Instant lastSessionEnd;

    // Helper methods
    public void recordHeartbeat() {
        this.lastHeartbeat = Instant.now();
        this.connected = true;
    }

    public void recordBootNotification() {
        this.lastBootNotification = Instant.now();
        this.connected = true;
    }

    public void disconnect() {
        this.connected = false;
    }

    public boolean isOnline() {
        if (!connected || lastHeartbeat == null) {
            return false;
        }
        
        Instant timeout = lastHeartbeat.plusSeconds(
            (heartbeatInterval != null ? heartbeatInterval : 300) + connectionTimeout
        );
        return Instant.now().isBefore(timeout);
    }

    public void enterMaintenanceMode(String reason) {
        this.maintenanceMode = true;
        this.maintenanceReason = reason;
        this.availability = StationAvailability.INOPERATIVE;
    }

    public void exitMaintenanceMode() {
        this.maintenanceMode = false;
        this.maintenanceReason = null;
        this.availability = StationAvailability.OPERATIVE;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
        this.availability = StationAvailability.INOPERATIVE;
    }

    public boolean hasSmartChargingSupport() {
        return smartChargingEnabled && 
               chargeProfileMaxStackLevel != null && 
               chargeProfileMaxStackLevel > 0;
    }

    public void addConnector(Connector connector) {
        connectors.add(connector);
        connector.setChargingStation(this);
    }

    public void removeConnector(Connector connector) {
        connectors.remove(connector);
        connector.setChargingStation(null);
    }

    public int getAvailableConnectors() {
        return (int) connectors.stream()
            .filter(connector -> connector.getStatus() == ConnectorStatus.AVAILABLE)
            .count();
    }

    public int getOccupiedConnectors() {
        return (int) connectors.stream()
            .filter(connector -> connector.getStatus() == ConnectorStatus.OCCUPIED)
            .count();
    }

    public boolean isOperational() {
        return active && 
               !maintenanceMode && 
               availability == StationAvailability.OPERATIVE &&
               (status == StationStatus.AVAILABLE || status == StationStatus.OCCUPIED);
    }

    public enum StationStatus {
        AVAILABLE,      // Station is available for charging
        OCCUPIED,       // At least one connector is occupied
        FAULTED,        // Station has a fault
        UNAVAILABLE,    // Station is unavailable
        RESERVED,       // Station is reserved
        UNKNOWN         // Status is unknown
    }

    public enum StationAvailability {
        INOPERATIVE,    // Station is not operative
        OPERATIVE       // Station is operative
    }
}