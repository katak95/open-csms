package com.opencsms.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.domain.station.Connector;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Connector entity.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorDto {

    private UUID id;

    @NotNull(message = "Connector ID is required")
    @Min(value = 1, message = "Connector ID must be at least 1")
    @Max(value = 50, message = "Connector ID cannot exceed 50")
    private Integer connectorId;

    @NotNull(message = "Connector type is required")
    private Connector.ConnectorType connectorType;

    @NotNull(message = "Connector standard is required")
    private Connector.ConnectorStandard standard;

    @NotNull(message = "Power type is required")
    private Connector.PowerType powerType;

    // Power specifications
    @DecimalMin(value = "0.1", message = "Max voltage must be greater than 0")
    @DecimalMax(value = "1000.0", message = "Max voltage cannot exceed 1000V")
    private BigDecimal maxVoltage;

    @DecimalMin(value = "0.1", message = "Max amperage must be greater than 0")
    @DecimalMax(value = "1000.0", message = "Max amperage cannot exceed 1000A")
    private BigDecimal maxAmperage;

    @DecimalMin(value = "0.1", message = "Max electric power must be greater than 0")
    @DecimalMax(value = "1000.0", message = "Max electric power cannot exceed 1000 kW")
    private BigDecimal maxElectricPower;

    // Status
    private Connector.ConnectorStatus status;
    private String errorCode;
    private String errorInfo;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant statusUpdatedAt;

    // Availability
    private Boolean available;
    private Boolean maintenanceMode;

    // Transaction info
    private Integer currentTransactionId;
    private String currentIdTag;
    private String currentUserId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant currentSessionStart;

    // Reservation info
    private Integer reservationId;
    private String reservedIdTag;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reservationExpiresAt;

    // Meter readings
    @DecimalMin(value = "0.0", message = "Total energy reading cannot be negative")
    private BigDecimal totalEnergyReadingKwh;

    @DecimalMin(value = "0.0", message = "Last meter reading cannot be negative")
    private BigDecimal lastMeterReadingKwh;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastMeterReadingAt;

    // Pricing
    @DecimalMin(value = "0.0", message = "Price per kWh cannot be negative")
    private BigDecimal pricePerKwh;

    @DecimalMin(value = "0.0", message = "Price per minute cannot be negative")
    private BigDecimal pricePerMinute;

    @DecimalMin(value = "0.0", message = "Price per session cannot be negative")
    private BigDecimal pricePerSession;

    // Statistics
    private Long totalSessions;
    private BigDecimal totalEnergyDeliveredKwh;
    private BigDecimal totalRevenue;
    private BigDecimal uptimePercentage;

    // Audit fields
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;

    private String createdBy;
    private String updatedBy;
    private Long version;

    // Parent station reference
    private UUID chargingStationId;

    /**
     * Create DTO from entity.
     */
    public static ConnectorDto fromEntity(Connector entity) {
        if (entity == null) {
            return null;
        }

        ConnectorDto dto = new ConnectorDto();
        dto.setId(entity.getId());
        dto.setConnectorId(entity.getConnectorId());
        dto.setConnectorType(entity.getConnectorType());
        dto.setStandard(entity.getStandard());
        dto.setPowerType(entity.getPowerType());
        dto.setMaxVoltage(entity.getMaxVoltage());
        dto.setMaxAmperage(entity.getMaxAmperage());
        dto.setMaxElectricPower(entity.getMaxElectricPower());
        dto.setStatus(entity.getStatus());
        dto.setErrorCode(entity.getErrorCode());
        dto.setErrorInfo(entity.getErrorInfo());
        dto.setStatusUpdatedAt(entity.getStatusUpdatedAt());
        dto.setAvailable(entity.isAvailable());
        dto.setMaintenanceMode(entity.isMaintenanceMode());
        dto.setCurrentTransactionId(entity.getCurrentTransactionId());
        dto.setCurrentIdTag(entity.getCurrentIdTag());
        dto.setCurrentUserId(entity.getCurrentUserId());
        dto.setCurrentSessionStart(entity.getCurrentSessionStart());
        dto.setReservationId(entity.getReservationId());
        dto.setReservedIdTag(entity.getReservedIdTag());
        dto.setReservationExpiresAt(entity.getReservationExpiresAt());
        dto.setTotalEnergyReadingKwh(entity.getTotalEnergyReadingKwh());
        dto.setLastMeterReadingKwh(entity.getLastMeterReadingKwh());
        dto.setLastMeterReadingAt(entity.getLastMeterReadingAt());
        dto.setPricePerKwh(entity.getPricePerKwh());
        dto.setPricePerMinute(entity.getPricePerMinute());
        dto.setPricePerSession(entity.getPricePerSession());
        dto.setTotalSessions(entity.getTotalSessions());
        dto.setTotalEnergyDeliveredKwh(entity.getTotalEnergyDeliveredKwh());
        dto.setTotalRevenue(entity.getTotalRevenue());
        dto.setUptimePercentage(entity.getUptimePercentage());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setVersion(entity.getVersion());
        
        if (entity.getChargingStation() != null) {
            dto.setChargingStationId(entity.getChargingStation().getId());
        }

        return dto;
    }

    /**
     * Convert DTO to entity.
     */
    public Connector toEntity() {
        Connector entity = new Connector();
        
        if (id != null) {
            entity.setId(id);
        }
        
        entity.setConnectorId(connectorId);
        entity.setConnectorType(connectorType);
        entity.setStandard(standard);
        entity.setPowerType(powerType);
        entity.setMaxVoltage(maxVoltage);
        entity.setMaxAmperage(maxAmperage);
        entity.setMaxElectricPower(maxElectricPower);
        
        if (status != null) {
            entity.setStatus(status);
        }
        
        entity.setErrorCode(errorCode);
        entity.setErrorInfo(errorInfo);
        entity.setAvailable(available != null ? available : true);
        entity.setMaintenanceMode(maintenanceMode != null ? maintenanceMode : false);
        entity.setCurrentTransactionId(currentTransactionId);
        entity.setCurrentIdTag(currentIdTag);
        entity.setCurrentUserId(currentUserId);
        entity.setReservationId(reservationId);
        entity.setReservedIdTag(reservedIdTag);
        entity.setReservationExpiresAt(reservationExpiresAt);
        entity.setTotalEnergyReadingKwh(totalEnergyReadingKwh);
        entity.setLastMeterReadingKwh(lastMeterReadingKwh);
        entity.setPricePerKwh(pricePerKwh);
        entity.setPricePerMinute(pricePerMinute);
        entity.setPricePerSession(pricePerSession);
        entity.setTotalSessions(totalSessions);
        entity.setTotalEnergyDeliveredKwh(totalEnergyDeliveredKwh);
        entity.setTotalRevenue(totalRevenue);
        entity.setUptimePercentage(uptimePercentage);
        
        if (version != null) {
            entity.setVersion(version);
        }

        return entity;
    }
}