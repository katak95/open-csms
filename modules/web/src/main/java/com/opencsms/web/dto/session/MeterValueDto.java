package com.opencsms.web.dto.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsms.domain.session.MeterValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for MeterValue operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeterValueDto {

    private UUID id;
    private UUID sessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    @NotNull(message = "Measurand is required")
    private MeterValue.Measurand measurand;

    @NotNull(message = "Value is required")
    private BigDecimal value;

    private String unit;
    private MeterValue.Context context;
    private MeterValue.Location location;
    private MeterValue.Phase phase;

    // Calculated convenience fields
    private BigDecimal energyKwh;
    private BigDecimal powerKw;
    private BigDecimal currentA;
    private BigDecimal voltageV;
    private BigDecimal socPercent;
    private BigDecimal temperatureC;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant createdAt;

    /**
     * Request DTO for adding meter values
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddMeterValueRequest {

        @NotNull(message = "Measurand is required")
        private MeterValue.Measurand measurand;

        @NotNull(message = "Value is required")
        private BigDecimal value;

        private String unit;
        private MeterValue.Context context = MeterValue.Context.SAMPLE_PERIODIC;
        private MeterValue.Location location = MeterValue.Location.OUTLET;
        private MeterValue.Phase phase;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
        private Instant timestamp;
    }
}