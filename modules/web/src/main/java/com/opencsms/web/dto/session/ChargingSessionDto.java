package com.opencsms.web.dto.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.domain.session.ChargingSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for ChargingSession operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingSessionDto {

    private UUID id;
    private UUID sessionUuid;

    @NotNull(message = "Connector ID is required", groups = {CreateSession.class})
    private UUID connectorId;

    private Integer connectorNumber;
    private UUID stationId;
    private String stationSerial;

    private UUID userId;
    private String username;

    private UUID authTokenId;
    private String authTokenValue;

    private ChargingSession.SessionStatus status;
    private Integer ocppTransactionId;
    private String ocppIdTag;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant endTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant authorizationTime;

    @DecimalMin(value = "0.0", message = "Meter start must be non-negative")
    private BigDecimal meterStart;

    @DecimalMin(value = "0.0", message = "Meter stop must be non-negative")
    private BigDecimal meterStop;

    private BigDecimal energyDeliveredKwh;
    private Integer durationMinutes;
    private BigDecimal maxPowerKw;
    private BigDecimal averagePowerKw;

    private ChargingSession.StopReason stopReason;
    private String failureReason;

    // Pricing information
    private UUID tariffId;
    private String currency;
    private BigDecimal pricePerKwh;
    private BigDecimal pricePerMinute;
    private BigDecimal sessionCost;
    private BigDecimal energyCost;
    private BigDecimal timeCost;
    private BigDecimal serviceFee;
    private BigDecimal totalCost;

    // Vehicle information
    private String vehicleId;
    private String licensePlate;

    // Remote control
    private Boolean remoteStart;
    private Boolean remoteStop;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant remoteStopRequested;

    // Additional metadata
    private String notes;
    private String errorCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant lastModifiedAt;

    // Nested objects for detailed view
    private List<MeterValueDto> meterValues;
    private List<SessionStatusHistoryDto> statusHistory;

    /**
     * Request DTO for creating a new session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {

        @NotNull(message = "Connector ID is required")
        private UUID connectorId;

        private UUID userId;
        private String idTag;
        private String vehicleId;
        private String licensePlate;
        private String notes;
        private Boolean remoteStart = false;
    }

    /**
     * Request DTO for starting a session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartSessionRequest {

        @NotBlank(message = "ID tag is required")
        private String idTag;

        @NotNull(message = "Transaction ID is required")
        private Integer transactionId;

        @NotNull(message = "Meter start value is required")
        @DecimalMin(value = "0.0", message = "Meter start must be non-negative")
        private BigDecimal meterStart;
    }

    /**
     * Request DTO for stopping a session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopSessionRequest {

        @NotNull(message = "Meter stop value is required")
        @DecimalMin(value = "0.0", message = "Meter stop must be non-negative")
        private BigDecimal meterStop;

        private ChargingSession.StopReason stopReason = ChargingSession.StopReason.REMOTE;
        private String notes;
    }

    /**
     * Request DTO for remote stop
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoteStopRequest {
        private String reason = "Remote stop requested";
    }

    /**
     * Summary DTO for session statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private UUID sessionId;
        private UUID sessionUuid;
        private ChargingSession.SessionStatus status;
        private String stationSerial;
        private Integer connectorNumber;
        private String username;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
        private Instant startTime;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
        private Instant endTime;

        private BigDecimal energyDeliveredKwh;
        private Integer durationMinutes;
        private BigDecimal totalCost;
        private String currency;
    }

    // Validation groups
    public interface CreateSession {}
    public interface StartSession {}
    public interface StopSession {}
}