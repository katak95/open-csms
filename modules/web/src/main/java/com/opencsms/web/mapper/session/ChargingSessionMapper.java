package com.opencsms.web.mapper.session;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.session.MeterValue;
import com.opencsms.domain.session.SessionStatusHistory;
import com.opencsms.web.dto.session.ChargingSessionDto;
import com.opencsms.web.dto.session.MeterValueDto;
import com.opencsms.web.dto.session.SessionStatusHistoryDto;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mapper for ChargingSession entities and DTOs.
 */
@Mapper(componentModel = "spring", 
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
@Component
public interface ChargingSessionMapper {

    /**
     * Convert entity to basic DTO
     */
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "authTokenValue", source = "authToken.tokenValue")
    ChargingSessionDto toDto(ChargingSession session);

    /**
     * Convert entity to detailed DTO with meter values and status history
     */
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "authTokenValue", source = "authToken.tokenValue")
    @Mapping(target = "meterValues", source = "meterValues")
    @Mapping(target = "statusHistory", source = "statusHistory")
    ChargingSessionDto toDetailedDto(ChargingSession session);

    /**
     * Convert entity to summary DTO
     */
    @Mapping(target = "sessionId", source = "id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "stationSerial", source = "stationSerial")
    @Mapping(target = "connectorNumber", source = "connectorNumber")
    ChargingSessionDto.SessionSummary toSummaryDto(ChargingSession session);

    /**
     * Convert list of entities to list of DTOs
     */
    List<ChargingSessionDto> toDtoList(List<ChargingSession> sessions);

    /**
     * Convert DTO to entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "authToken", ignore = true)
    @Mapping(target = "connector", ignore = true)
    @Mapping(target = "meterValues", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    ChargingSession toEntity(ChargingSessionDto dto);

    /**
     * Convert create request to entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessionUuid", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "authToken", ignore = true)
    @Mapping(target = "connector", ignore = true)
    @Mapping(target = "meterValues", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "ocppIdTag", source = "idTag")
    ChargingSession fromCreateRequest(ChargingSessionDto.CreateSessionRequest request);

    /**
     * Convert MeterValue entity to DTO
     */
    MeterValueDto toMeterValueDto(MeterValue meterValue);

    /**
     * Convert MeterValue DTO to entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chargingSession", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    MeterValue fromMeterValueDto(MeterValueDto dto);

    /**
     * Convert AddMeterValueRequest to entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "chargingSession", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "energyKwh", ignore = true)
    @Mapping(target = "powerKw", ignore = true)
    @Mapping(target = "currentA", ignore = true)
    @Mapping(target = "voltageV", ignore = true)
    @Mapping(target = "socPercent", ignore = true)
    @Mapping(target = "temperatureC", ignore = true)
    MeterValue fromAddMeterValueRequest(MeterValueDto.AddMeterValueRequest request);

    /**
     * Convert SessionStatusHistory entity to DTO
     */
    SessionStatusHistoryDto toSessionStatusHistoryDto(SessionStatusHistory statusHistory);

    /**
     * Convert list of MeterValue entities to DTOs
     */
    List<MeterValueDto> toMeterValueDtoList(List<MeterValue> meterValues);

    /**
     * Convert list of SessionStatusHistory entities to DTOs
     */
    List<SessionStatusHistoryDto> toSessionStatusHistoryDtoList(List<SessionStatusHistory> statusHistories);

    /**
     * Update entity from DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessionUuid", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "authToken", ignore = true)
    @Mapping(target = "connector", ignore = true)
    @Mapping(target = "meterValues", ignore = true)
    @Mapping(target = "statusHistory", ignore = true)
    void updateEntityFromDto(ChargingSessionDto dto, @MappingTarget ChargingSession session);

    /**
     * Set timestamp if not provided
     */
    @AfterMapping
    default void setTimestampIfNull(@MappingTarget MeterValue meterValue) {
        if (meterValue.getTimestamp() == null) {
            meterValue.setTimestamp(Instant.now());
        }
    }

    /**
     * Process meter value after mapping
     */
    @AfterMapping  
    default void processMeterValue(@MappingTarget MeterValue meterValue) {
        meterValue.processValue();
    }
}