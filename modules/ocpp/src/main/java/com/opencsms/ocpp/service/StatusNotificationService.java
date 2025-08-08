package com.opencsms.ocpp.service;

import com.opencsms.domain.station.ChargingStation;
import com.opencsms.domain.station.Connector;
import com.opencsms.domain.station.ConnectorRepository;
import com.opencsms.ocpp.message.ParsedOcppMessage;
import com.opencsms.ocpp.message.v16.request.StatusNotificationRequest;
import com.opencsms.ocpp.message.v16.response.StatusNotificationResponse;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.service.station.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for handling OCPP StatusNotification messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusNotificationService {

    private final StationService stationService;
    private final ConnectorRepository connectorRepository;

    /**
     * Process StatusNotification message from charging station.
     */
    public Object handleStatusNotification(OcppSession session, ParsedOcppMessage message) {
        String stationId = session.getStationId();
        String tenantId = session.getTenantId();
        String ocppVersion = session.getOcppVersion();
        
        log.debug("Processing StatusNotification from station: {} (tenant: {}, version: {})", 
                stationId, tenantId, ocppVersion);
        
        if (ocppVersion.startsWith("1.6")) {
            return handleStatusNotification16(session, message);
        } else if (ocppVersion.startsWith("2.0")) {
            return handleStatusNotification201(session, message);
        } else {
            log.warn("Unsupported OCPP version for StatusNotification: {}", ocppVersion);
            return new StatusNotificationResponse(); // Empty response for unsupported versions
        }
    }

    private Object handleStatusNotification16(OcppSession session, ParsedOcppMessage message) {
        try {
            StatusNotificationRequest request = message.getPayloadAs(StatusNotificationRequest.class);
            String stationId = session.getStationId();
            String tenantId = session.getTenantId();
            
            log.info("StatusNotification 1.6 from station: {} - Connector: {}, Status: {}, Error: {}", 
                    stationId, request.getConnectorId(), request.getStatus(), request.getErrorCode());
            
            // Find the charging station
            Optional<ChargingStation> stationOpt = stationService.findByStationId(stationId);
            if (stationOpt.isEmpty()) {
                log.warn("Received StatusNotification for unknown station: {}", stationId);
                return new StatusNotificationResponse();
            }
            
            ChargingStation station = stationOpt.get();
            
            // Handle connector 0 (station-wide status)
            if (request.getConnectorId() == 0) {
                updateStationStatus(station, request);
            } else {
                // Handle specific connector status
                updateConnectorStatus(station, request, tenantId);
            }
            
            return new StatusNotificationResponse();
            
        } catch (Exception e) {
            log.error("Error processing StatusNotification 1.6 for station: {}", session.getStationId(), e);
            return new StatusNotificationResponse();
        }
    }

    private Object handleStatusNotification201(OcppSession session, ParsedOcppMessage message) {
        // OCPP 2.0.1 uses a different approach with TransactionEvent and StatusNotification
        // For now, we'll implement basic handling
        log.info("StatusNotification 2.0.1 from station: {} - Implementation pending", session.getStationId());
        
        // TODO: Implement OCPP 2.0.1 StatusNotification handling
        // This would involve parsing the different message structure and updating accordingly
        
        return new StatusNotificationResponse(); // Empty response for now
    }

    private void updateStationStatus(ChargingStation station, StatusNotificationRequest request) {
        try {
            ChargingStation.StationStatus newStatus = mapStatusToStationStatus(request.getStatus());
            
            if (!newStatus.equals(station.getStatus())) {
                log.info("Updating station {} status from {} to {}", 
                        station.getStationId(), station.getStatus(), newStatus);
                
                station.setStatus(newStatus);
                station.setStatusUpdatedAt(request.getTimestamp() != null ? request.getTimestamp() : Instant.now());
                
                // Update error information if present
                if (request.getErrorCode() != StatusNotificationRequest.ChargePointErrorCode.NoError) {
                    station.setErrorCode(request.getErrorCode().name());
                    station.setErrorInfo(request.getInfo());
                } else {
                    station.setErrorCode(null);
                    station.setErrorInfo(null);
                }
                
                stationService.updateStation(station.getId(), station);
            }
            
        } catch (Exception e) {
            log.error("Error updating station status for {}", station.getStationId(), e);
        }
    }

    private void updateConnectorStatus(ChargingStation station, StatusNotificationRequest request, String tenantId) {
        try {
            // Find the connector
            Optional<Connector> connectorOpt = connectorRepository.findByStationIdAndConnectorId(
                station.getStationId(), request.getConnectorId(), tenantId);
            
            if (connectorOpt.isEmpty()) {
                log.warn("Received StatusNotification for unknown connector: {} on station: {}", 
                        request.getConnectorId(), station.getStationId());
                return;
            }
            
            Connector connector = connectorOpt.get();
            Connector.ConnectorStatus newStatus = mapStatusToConnectorStatus(request.getStatus());
            
            if (!newStatus.equals(connector.getStatus())) {
                log.info("Updating connector {}.{} status from {} to {}", 
                        station.getStationId(), connector.getConnectorId(), 
                        connector.getStatus(), newStatus);
                
                connector.setStatus(newStatus);
                connector.setStatusUpdatedAt(request.getTimestamp() != null ? request.getTimestamp() : Instant.now());
                
                // Update error information if present
                if (request.getErrorCode() != StatusNotificationRequest.ChargePointErrorCode.NoError) {
                    connector.setErrorCode(request.getErrorCode().name());
                    connector.setErrorInfo(request.getInfo());
                } else {
                    connector.setErrorCode(null);
                    connector.setErrorInfo(null);
                }
                
                // Update availability based on status
                connector.setAvailable(isStatusAvailable(newStatus));
                
                connectorRepository.save(connector);
                
                // Update station status based on connector states
                updateStationStatusFromConnectors(station);
            }
            
        } catch (Exception e) {
            log.error("Error updating connector status for {}.{}", 
                    station.getStationId(), request.getConnectorId(), e);
        }
    }

    private void updateStationStatusFromConnectors(ChargingStation station) {
        try {
            // Get all connectors for the station
            var connectors = connectorRepository.findByStationId(station.getStationId(), station.getTenantId());
            
            if (connectors.isEmpty()) {
                return;
            }
            
            // Determine station status based on connector states
            boolean anyAvailable = connectors.stream().anyMatch(c -> c.getStatus() == Connector.ConnectorStatus.AVAILABLE);
            boolean anyOccupied = connectors.stream().anyMatch(c -> c.getStatus() == Connector.ConnectorStatus.OCCUPIED);
            boolean anyFaulted = connectors.stream().anyMatch(c -> c.getStatus() == Connector.ConnectorStatus.FAULTED);
            boolean allUnavailable = connectors.stream().allMatch(c -> c.getStatus() == Connector.ConnectorStatus.UNAVAILABLE);
            
            ChargingStation.StationStatus newStationStatus;
            
            if (anyFaulted) {
                newStationStatus = ChargingStation.StationStatus.FAULTED;
            } else if (anyOccupied) {
                newStationStatus = ChargingStation.StationStatus.OCCUPIED;
            } else if (anyAvailable) {
                newStationStatus = ChargingStation.StationStatus.AVAILABLE;
            } else if (allUnavailable) {
                newStationStatus = ChargingStation.StationStatus.UNAVAILABLE;
            } else {
                newStationStatus = ChargingStation.StationStatus.UNKNOWN;
            }
            
            if (!newStationStatus.equals(station.getStatus())) {
                log.info("Updating station {} derived status to {}", station.getStationId(), newStationStatus);
                station.setStatus(newStationStatus);
                station.setStatusUpdatedAt(Instant.now());
                stationService.updateStation(station.getId(), station);
            }
            
        } catch (Exception e) {
            log.error("Error updating station status from connectors for {}", station.getStationId(), e);
        }
    }

    private ChargingStation.StationStatus mapStatusToStationStatus(StatusNotificationRequest.ChargePointStatus status) {
        switch (status) {
            case Available:
                return ChargingStation.StationStatus.AVAILABLE;
            case Preparing:
            case Charging:
            case SuspendedEVSE:
            case SuspendedEV:
            case Finishing:
                return ChargingStation.StationStatus.OCCUPIED;
            case Reserved:
                return ChargingStation.StationStatus.RESERVED;
            case Unavailable:
                return ChargingStation.StationStatus.UNAVAILABLE;
            case Faulted:
                return ChargingStation.StationStatus.FAULTED;
            default:
                return ChargingStation.StationStatus.UNKNOWN;
        }
    }

    private Connector.ConnectorStatus mapStatusToConnectorStatus(StatusNotificationRequest.ChargePointStatus status) {
        switch (status) {
            case Available:
                return Connector.ConnectorStatus.AVAILABLE;
            case Preparing:
                return Connector.ConnectorStatus.PREPARING;
            case Charging:
                return Connector.ConnectorStatus.CHARGING;
            case SuspendedEVSE:
                return Connector.ConnectorStatus.SUSPENDED_EVSE;
            case SuspendedEV:
                return Connector.ConnectorStatus.SUSPENDED_EV;
            case Finishing:
                return Connector.ConnectorStatus.FINISHING;
            case Reserved:
                return Connector.ConnectorStatus.RESERVED;
            case Unavailable:
                return Connector.ConnectorStatus.UNAVAILABLE;
            case Faulted:
                return Connector.ConnectorStatus.FAULTED;
            default:
                return Connector.ConnectorStatus.UNKNOWN;
        }
    }

    private boolean isStatusAvailable(Connector.ConnectorStatus status) {
        return status == Connector.ConnectorStatus.AVAILABLE || 
               status == Connector.ConnectorStatus.RESERVED;
    }

    /**
     * Get status notification statistics for monitoring.
     */
    public StatusNotificationStatistics getStatistics(String tenantId) {
        try {
            long totalStations = stationService.findAllActive().size();
            
            var stationsByStatus = stationService.findAllActive().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    ChargingStation::getStatus,
                    java.util.stream.Collectors.counting()
                ));
            
            return StatusNotificationStatistics.builder()
                .totalStations(totalStations)
                .availableStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.AVAILABLE, 0L))
                .occupiedStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.OCCUPIED, 0L))
                .faultedStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.FAULTED, 0L))
                .unavailableStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.UNAVAILABLE, 0L))
                .unknownStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.UNKNOWN, 0L))
                .reservedStations(stationsByStatus.getOrDefault(ChargingStation.StationStatus.RESERVED, 0L))
                .lastUpdateTime(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error getting status notification statistics", e);
            return StatusNotificationStatistics.builder()
                .totalStations(0)
                .lastUpdateTime(Instant.now())
                .build();
        }
    }

    /**
     * Status notification statistics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class StatusNotificationStatistics {
        private long totalStations;
        private long availableStations;
        private long occupiedStations;
        private long faultedStations;
        private long unavailableStations;
        private long unknownStations;
        private long reservedStations;
        private Instant lastUpdateTime;
        
        public double getAvailablePercentage() {
            return totalStations > 0 ? (double) availableStations / totalStations * 100.0 : 0.0;
        }
        
        public double getOccupiedPercentage() {
            return totalStations > 0 ? (double) occupiedStations / totalStations * 100.0 : 0.0;
        }
        
        public double getFaultedPercentage() {
            return totalStations > 0 ? (double) faultedStations / totalStations * 100.0 : 0.0;
        }
    }
}