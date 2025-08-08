package com.opencsms.service.station;

import com.opencsms.domain.station.ChargingStation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service for publishing station-related events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StationEventService {

    private final ApplicationEventPublisher eventPublisher;

    public void publishStationCreated(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_CREATED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station created event for: {}", station.getStationId());
    }

    public void publishStationUpdated(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_UPDATED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station updated event for: {}", station.getStationId());
    }

    public void publishStationDeleted(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_DELETED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station deleted event for: {}", station.getStationId());
    }

    public void publishStationActivated(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_ACTIVATED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station activated event for: {}", station.getStationId());
    }

    public void publishStationDeactivated(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_DEACTIVATED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station deactivated event for: {}", station.getStationId());
    }

    public void publishStationConnected(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_CONNECTED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station connected event for: {}", station.getStationId());
    }

    public void publishStationDisconnected(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_DISCONNECTED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station disconnected event for: {}", station.getStationId());
    }

    public void publishStationMaintenanceStarted(ChargingStation station, String reason) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_MAINTENANCE_STARTED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .message(reason)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station maintenance started event for: {}", station.getStationId());
    }

    public void publishStationMaintenanceEnded(ChargingStation station) {
        StationEvent event = StationEvent.builder()
            .eventType(StationEventType.STATION_MAINTENANCE_ENDED)
            .stationId(station.getStationId())
            .tenantId(station.getTenantId())
            .station(station)
            .build();
        
        eventPublisher.publishEvent(event);
        log.debug("Published station maintenance ended event for: {}", station.getStationId());
    }

    public enum StationEventType {
        STATION_CREATED,
        STATION_UPDATED,
        STATION_DELETED,
        STATION_ACTIVATED,
        STATION_DEACTIVATED,
        STATION_CONNECTED,
        STATION_DISCONNECTED,
        STATION_MAINTENANCE_STARTED,
        STATION_MAINTENANCE_ENDED
    }
}