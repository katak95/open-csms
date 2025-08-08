package com.opencsms.ocpp.service;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.station.ChargingStation;
import com.opencsms.ocpp.message.ParsedOcppMessage;
import com.opencsms.ocpp.message.v16.request.BootNotificationRequest;
import com.opencsms.ocpp.message.v16.response.BootNotificationResponse;
import com.opencsms.ocpp.message.v201.request.BootNotificationRequest as BootNotificationRequest201;
import com.opencsms.ocpp.message.v201.response.BootNotificationResponse as BootNotificationResponse201;
import com.opencsms.ocpp.session.OcppSession;
import com.opencsms.service.station.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for handling OCPP BootNotification messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BootNotificationService {

    private final StationService stationService;

    /**
     * Process BootNotification message from charging station.
     */
    public Object handleBootNotification(OcppSession session, ParsedOcppMessage message) {
        String stationId = session.getStationId();
        String tenantId = session.getTenantId();
        String ocppVersion = session.getOcppVersion();
        
        log.info("Processing BootNotification from station: {} (tenant: {}, version: {})", 
                stationId, tenantId, ocppVersion);
        
        if (ocppVersion.startsWith("1.6")) {
            return handleBootNotification16(session, message);
        } else if (ocppVersion.startsWith("2.0")) {
            return handleBootNotification201(session, message);
        } else {
            log.warn("Unsupported OCPP version for BootNotification: {}", ocppVersion);
            return createRejectedResponse(ocppVersion, "Unsupported OCPP version");
        }
    }

    private Object handleBootNotification16(OcppSession session, ParsedOcppMessage message) {
        try {
            BootNotificationRequest request = message.getPayloadAs(BootNotificationRequest.class);
            String stationId = session.getStationId();
            String tenantId = session.getTenantId();
            
            log.info("BootNotification 1.6 from station: {} - Model: {}, Vendor: {}", 
                    stationId, request.getChargePointModel(), request.getChargePointVendor());
            
            // Get or create charging station
            Optional<ChargingStation> stationOpt = stationService.findByStationId(stationId);
            ChargingStation station;
            
            if (stationOpt.isPresent()) {
                station = stationOpt.get();
                log.info("Found existing station: {}", stationId);
            } else {
                // Create new station from boot notification
                station = createStationFromBootNotification16(stationId, tenantId, request);
                station = stationService.createStation(station);
                log.info("Created new station from BootNotification: {}", stationId);
            }
            
            // Update station information from boot notification
            updateStationFromBootNotification16(station, request);
            station = stationService.updateStation(station.getId(), station);
            
            // Record boot notification
            stationService.recordBootNotification(stationId);
            
            // Mark session as authenticated
            session.setAuthenticated(true);
            session.setBootNotificationStatus("Accepted");
            
            // Determine acceptance status
            BootNotificationResponse.RegistrationStatus status = determineRegistrationStatus(station);
            Integer heartbeatInterval = station.getHeartbeatInterval() != null ? 
                station.getHeartbeatInterval() : 300; // Default 5 minutes
            
            BootNotificationResponse response = new BootNotificationResponse(
                status,
                Instant.now(),
                heartbeatInterval
            );
            
            log.info("BootNotification response for station {}: {}", stationId, status);
            return response;
            
        } catch (Exception e) {
            log.error("Error processing BootNotification 1.6 for station: {}", session.getStationId(), e);
            return new BootNotificationResponse(
                BootNotificationResponse.RegistrationStatus.Rejected,
                Instant.now(),
                300
            );
        }
    }

    private Object handleBootNotification201(OcppSession session, ParsedOcppMessage message) {
        try {
            BootNotificationRequest201 request = message.getPayloadAs(BootNotificationRequest201.class);
            String stationId = session.getStationId();
            String tenantId = session.getTenantId();
            
            log.info("BootNotification 2.0.1 from station: {} - Model: {}, Vendor: {}, Reason: {}", 
                    stationId, 
                    request.getChargingStation().getModel(),
                    request.getChargingStation().getVendorName(),
                    request.getReason());
            
            // Get or create charging station
            Optional<ChargingStation> stationOpt = stationService.findByStationId(stationId);
            ChargingStation station;
            
            if (stationOpt.isPresent()) {
                station = stationOpt.get();
                log.info("Found existing station: {}", stationId);
            } else {
                // Create new station from boot notification
                station = createStationFromBootNotification201(stationId, tenantId, request);
                station = stationService.createStation(station);
                log.info("Created new station from BootNotification: {}", stationId);
            }
            
            // Update station information from boot notification
            updateStationFromBootNotification201(station, request);
            station = stationService.updateStation(station.getId(), station);
            
            // Record boot notification
            stationService.recordBootNotification(stationId);
            
            // Mark session as authenticated
            session.setAuthenticated(true);
            session.setBootNotificationStatus("Accepted");
            
            // Determine acceptance status
            BootNotificationResponse201.RegistrationStatus status = 
                mapRegistrationStatus201(determineRegistrationStatus(station));
            Integer heartbeatInterval = station.getHeartbeatInterval() != null ? 
                station.getHeartbeatInterval() : 300;
            
            BootNotificationResponse201 response = new BootNotificationResponse201(
                Instant.now(),
                heartbeatInterval,
                status,
                null // StatusInfo - can be added for additional details
            );
            
            log.info("BootNotification response for station {}: {}", stationId, status);
            return response;
            
        } catch (Exception e) {
            log.error("Error processing BootNotification 2.0.1 for station: {}", session.getStationId(), e);
            return new BootNotificationResponse201(
                Instant.now(),
                300,
                BootNotificationResponse201.RegistrationStatus.Rejected,
                new BootNotificationResponse201.StatusInfo("InternalError", "Processing failed")
            );
        }
    }

    private ChargingStation createStationFromBootNotification16(String stationId, String tenantId, 
                                                              BootNotificationRequest request) {
        ChargingStation station = new ChargingStation();
        station.setStationId(stationId);
        station.setTenantId(tenantId);
        
        // Set basic information from BootNotification
        station.setName(request.getChargePointModel() + " - " + stationId);
        station.setDescription("Auto-created from BootNotification");
        
        // Set OCPP version
        station.setOcppVersion("1.6");
        
        // Set default values
        station.setStatus(ChargingStation.StationStatus.UNKNOWN);
        station.setActive(true);
        station.setHeartbeatInterval(300); // 5 minutes default
        station.setMeterValuesSampleInterval(60); // 1 minute default
        station.setConnectionTimeout(30); // 30 seconds default
        
        // Set vendor information
        if (request.getChargePointVendor() != null) {
            station.setOperatorName(request.getChargePointVendor());
        }
        
        // Add metadata with boot notification details
        station.getMetadata().put("chargePointModel", request.getChargePointModel());
        station.getMetadata().put("chargePointVendor", request.getChargePointVendor());
        if (request.getFirmwareVersion() != null) {
            station.getMetadata().put("firmwareVersion", request.getFirmwareVersion());
        }
        if (request.getChargeBoxSerialNumber() != null) {
            station.getMetadata().put("chargeBoxSerialNumber", request.getChargeBoxSerialNumber());
        }
        if (request.getChargePointSerialNumber() != null) {
            station.getMetadata().put("chargePointSerialNumber", request.getChargePointSerialNumber());
        }
        if (request.getMeterType() != null) {
            station.getMetadata().put("meterType", request.getMeterType());
        }
        if (request.getMeterSerialNumber() != null) {
            station.getMetadata().put("meterSerialNumber", request.getMeterSerialNumber());
        }
        if (request.getIccid() != null) {
            station.getMetadata().put("iccid", request.getIccid());
        }
        if (request.getImsi() != null) {
            station.getMetadata().put("imsi", request.getImsi());
        }
        
        return station;
    }

    private ChargingStation createStationFromBootNotification201(String stationId, String tenantId,
                                                               BootNotificationRequest201 request) {
        ChargingStation station = new ChargingStation();
        station.setStationId(stationId);
        station.setTenantId(tenantId);
        
        // Set basic information from BootNotification
        station.setName(request.getChargingStation().getModel() + " - " + stationId);
        station.setDescription("Auto-created from BootNotification (reason: " + request.getReason() + ")");
        
        // Set OCPP version
        station.setOcppVersion("2.0.1");
        
        // Set default values
        station.setStatus(ChargingStation.StationStatus.UNKNOWN);
        station.setActive(true);
        station.setHeartbeatInterval(300); // 5 minutes default
        station.setMeterValuesSampleInterval(60); // 1 minute default
        station.setConnectionTimeout(30); // 30 seconds default
        
        // Set vendor information
        station.setOperatorName(request.getChargingStation().getVendorName());
        
        // Add metadata with boot notification details
        station.getMetadata().put("model", request.getChargingStation().getModel());
        station.getMetadata().put("vendorName", request.getChargingStation().getVendorName());
        station.getMetadata().put("bootReason", request.getReason().name());
        
        if (request.getChargingStation().getSerialNumber() != null) {
            station.getMetadata().put("serialNumber", request.getChargingStation().getSerialNumber());
        }
        if (request.getChargingStation().getFirmwareVersion() != null) {
            station.getMetadata().put("firmwareVersion", request.getChargingStation().getFirmwareVersion());
        }
        if (request.getChargingStation().getModem() != null) {
            if (request.getChargingStation().getModem().getIccid() != null) {
                station.getMetadata().put("iccid", request.getChargingStation().getModem().getIccid());
            }
            if (request.getChargingStation().getModem().getImsi() != null) {
                station.getMetadata().put("imsi", request.getChargingStation().getModem().getImsi());
            }
        }
        
        return station;
    }

    private void updateStationFromBootNotification16(ChargingStation station, BootNotificationRequest request) {
        // Update firmware version if changed
        if (request.getFirmwareVersion() != null) {
            station.getMetadata().put("firmwareVersion", request.getFirmwareVersion());
        }
        
        // Update other metadata that might change
        station.getMetadata().put("lastBootNotification", Instant.now().toString());
        station.getMetadata().put("chargePointModel", request.getChargePointModel());
        station.getMetadata().put("chargePointVendor", request.getChargePointVendor());
    }

    private void updateStationFromBootNotification201(ChargingStation station, BootNotificationRequest201 request) {
        // Update firmware version if changed
        if (request.getChargingStation().getFirmwareVersion() != null) {
            station.getMetadata().put("firmwareVersion", request.getChargingStation().getFirmwareVersion());
        }
        
        // Update other metadata that might change
        station.getMetadata().put("lastBootNotification", Instant.now().toString());
        station.getMetadata().put("bootReason", request.getReason().name());
        station.getMetadata().put("model", request.getChargingStation().getModel());
        station.getMetadata().put("vendorName", request.getChargingStation().getVendorName());
    }

    private BootNotificationResponse.RegistrationStatus determineRegistrationStatus(ChargingStation station) {
        // Simple logic for now - can be enhanced with business rules
        if (station.isActive() && !station.isDeleted()) {
            return BootNotificationResponse.RegistrationStatus.Accepted;
        } else if (station.isDeleted()) {
            return BootNotificationResponse.RegistrationStatus.Rejected;
        } else {
            return BootNotificationResponse.RegistrationStatus.Pending;
        }
    }

    private BootNotificationResponse201.RegistrationStatus mapRegistrationStatus201(
            BootNotificationResponse.RegistrationStatus status) {
        switch (status) {
            case Accepted:
                return BootNotificationResponse201.RegistrationStatus.Accepted;
            case Pending:
                return BootNotificationResponse201.RegistrationStatus.Pending;
            case Rejected:
            default:
                return BootNotificationResponse201.RegistrationStatus.Rejected;
        }
    }

    private Object createRejectedResponse(String ocppVersion, String reason) {
        if (ocppVersion.startsWith("1.6")) {
            return new BootNotificationResponse(
                BootNotificationResponse.RegistrationStatus.Rejected,
                Instant.now(),
                300
            );
        } else {
            return new BootNotificationResponse201(
                Instant.now(),
                300,
                BootNotificationResponse201.RegistrationStatus.Rejected,
                new BootNotificationResponse201.StatusInfo("NotSupported", reason)
            );
        }
    }
}