package com.opencsms.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.domain.station.ChargingStation;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for ChargingStation entity.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingStationDto {

    private UUID id;

    @NotBlank(message = "Station ID is required")
    @Size(max = 100, message = "Station ID cannot exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Station ID can only contain alphanumeric characters, dashes, and underscores")
    private String stationId;

    @NotBlank(message = "Station name is required")
    @Size(max = 200, message = "Station name cannot exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    // Location fields
    @Size(max = 300, message = "Address cannot exceed 300 characters")
    private String address;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 2, min = 2, message = "Country must be a 2-character ISO code")
    private String country;

    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90 degrees")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90 degrees")
    private BigDecimal latitude;

    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180 degrees")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180 degrees")
    private BigDecimal longitude;

    private BigDecimal altitude;

    // Organization fields
    @Size(max = 100, message = "Network operator cannot exceed 100 characters")
    private String networkOperator;

    @Size(max = 100, message = "Operator name cannot exceed 100 characters")
    private String operatorName;

    @Size(max = 50, message = "Network ID cannot exceed 50 characters")
    private String networkId;

    // Technical specifications
    @DecimalMin(value = "0.1", message = "Max power must be greater than 0")
    @DecimalMax(value = "1000.0", message = "Max power cannot exceed 1000 kW")
    private BigDecimal maxPowerKw;

    @Min(value = 1, message = "Number of connectors must be at least 1")
    @Max(value = 50, message = "Number of connectors cannot exceed 50")
    private Integer numConnectors;

    // Status and operational fields
    private ChargingStation.StationStatus status;
    private Boolean active;
    private Boolean deleted;

    // OCPP Configuration
    @Size(max = 10, message = "OCPP version cannot exceed 10 characters")
    private String ocppVersion;

    @Min(value = 30, message = "Heartbeat interval must be at least 30 seconds")
    @Max(value = 3600, message = "Heartbeat interval cannot exceed 3600 seconds")
    private Integer heartbeatInterval;

    @Min(value = 5, message = "Meter values sample interval must be at least 5 seconds")
    @Max(value = 3600, message = "Meter values sample interval cannot exceed 3600 seconds")
    private Integer meterValuesSampleInterval;

    @Min(value = 10, message = "Connection timeout must be at least 10 seconds")
    @Max(value = 600, message = "Connection timeout cannot exceed 600 seconds")
    private Integer connectionTimeout;

    private Boolean localAuthorizeOffline;
    private Boolean localPreAuthorize;
    private Boolean allowOfflineTxForUnknownId;
    private Boolean authorizationCacheEnabled;

    // Smart Charging
    private Boolean smartChargingEnabled;

    @Min(value = 1, message = "Charge profile max stack level must be at least 1")
    @Max(value = 100, message = "Charge profile max stack level cannot exceed 100")
    private Integer chargeProfileMaxStackLevel;

    // Connection info
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastHeartbeat;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastBootNotification;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant connectedSince;

    private String websocketUrl;

    // Statistics
    private Long totalSessions;
    private BigDecimal totalEnergyDeliveredKwh;
    private BigDecimal totalRevenue;
    private BigDecimal uptimePercentage;

    // Maintenance
    private Boolean maintenanceMode;
    private String maintenanceReason;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant maintenanceStartedAt;

    // Metadata
    private Map<String, Object> metadata;

    // Audit fields
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;

    private String createdBy;
    private String updatedBy;
    private Long version;

    // Relationships
    private List<ConnectorDto> connectors;

    /**
     * Create DTO from entity.
     */
    public static ChargingStationDto fromEntity(ChargingStation entity) {
        if (entity == null) {
            return null;
        }

        ChargingStationDto dto = new ChargingStationDto();
        dto.setId(entity.getId());
        dto.setStationId(entity.getStationId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setAddress(entity.getAddress());
        dto.setCity(entity.getCity());
        dto.setPostalCode(entity.getPostalCode());
        dto.setCountry(entity.getCountry());
        dto.setLatitude(entity.getLatitude());
        dto.setLongitude(entity.getLongitude());
        dto.setAltitude(entity.getAltitude());
        dto.setNetworkOperator(entity.getNetworkOperator());
        dto.setOperatorName(entity.getOperatorName());
        dto.setNetworkId(entity.getNetworkId());
        dto.setMaxPowerKw(entity.getMaxPowerKw());
        dto.setNumConnectors(entity.getNumConnectors());
        dto.setStatus(entity.getStatus());
        dto.setActive(entity.isActive());
        dto.setDeleted(entity.isDeleted());
        dto.setOcppVersion(entity.getOcppVersion());
        dto.setHeartbeatInterval(entity.getHeartbeatInterval());
        dto.setMeterValuesSampleInterval(entity.getMeterValuesSampleInterval());
        dto.setConnectionTimeout(entity.getConnectionTimeout());
        dto.setLocalAuthorizeOffline(entity.isLocalAuthorizeOffline());
        dto.setLocalPreAuthorize(entity.isLocalPreAuthorize());
        dto.setAllowOfflineTxForUnknownId(entity.isAllowOfflineTxForUnknownId());
        dto.setAuthorizationCacheEnabled(entity.isAuthorizationCacheEnabled());
        dto.setSmartChargingEnabled(entity.isSmartChargingEnabled());
        dto.setChargeProfileMaxStackLevel(entity.getChargeProfileMaxStackLevel());
        dto.setLastHeartbeat(entity.getLastHeartbeat());
        dto.setLastBootNotification(entity.getLastBootNotification());
        dto.setConnectedSince(entity.getConnectedSince());
        dto.setWebsocketUrl(entity.getWebsocketUrl());
        dto.setTotalSessions(entity.getTotalSessions());
        dto.setTotalEnergyDeliveredKwh(entity.getTotalEnergyDeliveredKwh());
        dto.setTotalRevenue(entity.getTotalRevenue());
        dto.setUptimePercentage(entity.getUptimePercentage());
        dto.setMaintenanceMode(entity.isMaintenanceMode());
        dto.setMaintenanceReason(entity.getMaintenanceReason());
        dto.setMaintenanceStartedAt(entity.getMaintenanceStartedAt());
        dto.setMetadata(entity.getMetadata());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setVersion(entity.getVersion());

        return dto;
    }

    /**
     * Convert DTO to entity.
     */
    public ChargingStation toEntity() {
        ChargingStation entity = new ChargingStation();
        
        if (id != null) {
            entity.setId(id);
        }
        
        entity.setStationId(stationId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setAddress(address);
        entity.setCity(city);
        entity.setPostalCode(postalCode);
        entity.setCountry(country);
        entity.setLatitude(latitude);
        entity.setLongitude(longitude);
        entity.setAltitude(altitude);
        entity.setNetworkOperator(networkOperator);
        entity.setOperatorName(operatorName);
        entity.setNetworkId(networkId);
        entity.setMaxPowerKw(maxPowerKw);
        entity.setNumConnectors(numConnectors);
        
        if (status != null) {
            entity.setStatus(status);
        }
        
        entity.setOcppVersion(ocppVersion);
        entity.setHeartbeatInterval(heartbeatInterval);
        entity.setMeterValuesSampleInterval(meterValuesSampleInterval);
        entity.setConnectionTimeout(connectionTimeout);
        entity.setLocalAuthorizeOffline(localAuthorizeOffline != null ? localAuthorizeOffline : false);
        entity.setLocalPreAuthorize(localPreAuthorize != null ? localPreAuthorize : false);
        entity.setAllowOfflineTxForUnknownId(allowOfflineTxForUnknownId != null ? allowOfflineTxForUnknownId : false);
        entity.setAuthorizationCacheEnabled(authorizationCacheEnabled != null ? authorizationCacheEnabled : false);
        entity.setSmartChargingEnabled(smartChargingEnabled != null ? smartChargingEnabled : false);
        entity.setChargeProfileMaxStackLevel(chargeProfileMaxStackLevel);
        entity.setWebsocketUrl(websocketUrl);
        entity.setMaintenanceMode(maintenanceMode != null ? maintenanceMode : false);
        entity.setMaintenanceReason(maintenanceReason);
        
        if (metadata != null) {
            entity.setMetadata(metadata);
        }
        
        if (version != null) {
            entity.setVersion(version);
        }

        return entity;
    }
}