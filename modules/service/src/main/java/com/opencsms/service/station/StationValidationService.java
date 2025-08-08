package com.opencsms.service.station;

import com.opencsms.domain.station.ChargingStation;
import com.opencsms.domain.station.Connector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Service for validating charging station data.
 */
@Service
@Slf4j
public class StationValidationService {

    /**
     * Validate charging station data.
     */
    public void validateStation(ChargingStation station) {
        validateRequired(station);
        validateBusinessRules(station);
        validateTechnicalSpecs(station);
        validateLocation(station);
    }

    /**
     * Validate connector data.
     */
    public void validateConnector(Connector connector) {
        validateRequiredConnector(connector);
        validateConnectorBusinessRules(connector);
        validateConnectorTechnicalSpecs(connector);
    }

    private void validateRequired(ChargingStation station) {
        if (!StringUtils.hasText(station.getStationId())) {
            throw new IllegalArgumentException("Station ID is required");
        }
        
        if (station.getStationId().length() > 100) {
            throw new IllegalArgumentException("Station ID cannot exceed 100 characters");
        }
        
        if (!StringUtils.hasText(station.getName())) {
            throw new IllegalArgumentException("Station name is required");
        }
        
        if (station.getName().length() > 200) {
            throw new IllegalArgumentException("Station name cannot exceed 200 characters");
        }

        // Validate station ID format (alphanumeric, dash, underscore only)
        if (!station.getStationId().matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Station ID can only contain alphanumeric characters, dashes, and underscores");
        }
    }

    private void validateBusinessRules(ChargingStation station) {
        // Validate heartbeat interval
        if (station.getHeartbeatInterval() != null && 
            (station.getHeartbeatInterval() < 30 || station.getHeartbeatInterval() > 3600)) {
            throw new IllegalArgumentException("Heartbeat interval must be between 30 and 3600 seconds");
        }
        
        // Validate meter values sample interval
        if (station.getMeterValuesSampleInterval() != null && 
            (station.getMeterValuesSampleInterval() < 5 || station.getMeterValuesSampleInterval() > 3600)) {
            throw new IllegalArgumentException("Meter values sample interval must be between 5 and 3600 seconds");
        }
        
        // Validate connection timeout
        if (station.getConnectionTimeout() != null && 
            (station.getConnectionTimeout() < 10 || station.getConnectionTimeout() > 600)) {
            throw new IllegalArgumentException("Connection timeout must be between 10 and 600 seconds");
        }
        
        // Validate number of connectors
        if (station.getNumConnectors() != null && 
            (station.getNumConnectors() < 1 || station.getNumConnectors() > 50)) {
            throw new IllegalArgumentException("Number of connectors must be between 1 and 50");
        }
    }

    private void validateTechnicalSpecs(ChargingStation station) {
        // Validate max power
        if (station.getMaxPowerKw() != null) {
            if (station.getMaxPowerKw().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Max power must be greater than 0");
            }
            if (station.getMaxPowerKw().compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new IllegalArgumentException("Max power cannot exceed 1000 kW");
            }
        }
        
        // Validate OCPP version
        if (StringUtils.hasText(station.getOcppVersion())) {
            String version = station.getOcppVersion();
            if (!version.equals("1.5") && !version.equals("1.6") && !version.equals("2.0") && !version.equals("2.0.1")) {
                throw new IllegalArgumentException("Unsupported OCPP version: " + version);
            }
        }
        
        // Validate smart charging settings
        if (station.isSmartChargingEnabled()) {
            if (station.getChargeProfileMaxStackLevel() == null || 
                station.getChargeProfileMaxStackLevel() < 1 || 
                station.getChargeProfileMaxStackLevel() > 100) {
                throw new IllegalArgumentException("Charge profile max stack level must be between 1 and 100 when smart charging is enabled");
            }
        }
        
        // Validate websocket URL format if provided
        if (StringUtils.hasText(station.getWebsocketUrl())) {
            String url = station.getWebsocketUrl().toLowerCase();
            if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
                throw new IllegalArgumentException("WebSocket URL must start with ws:// or wss://");
            }
        }
    }

    private void validateLocation(ChargingStation station) {
        // Validate latitude
        if (station.getLatitude() != null) {
            BigDecimal lat = station.getLatitude();
            if (lat.compareTo(BigDecimal.valueOf(-90)) < 0 || lat.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");
            }
        }
        
        // Validate longitude
        if (station.getLongitude() != null) {
            BigDecimal lng = station.getLongitude();
            if (lng.compareTo(BigDecimal.valueOf(-180)) < 0 || lng.compareTo(BigDecimal.valueOf(180)) > 0) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");
            }
        }
        
        // Validate country code
        if (StringUtils.hasText(station.getCountry())) {
            if (station.getCountry().length() != 2) {
                throw new IllegalArgumentException("Country must be a 2-character ISO code");
            }
        }
        
        // Validate postal code length
        if (StringUtils.hasText(station.getPostalCode()) && station.getPostalCode().length() > 20) {
            throw new IllegalArgumentException("Postal code cannot exceed 20 characters");
        }
    }

    private void validateRequiredConnector(Connector connector) {
        if (connector.getConnectorId() == null) {
            throw new IllegalArgumentException("Connector ID is required");
        }
        
        if (connector.getConnectorId() < 1 || connector.getConnectorId() > 50) {
            throw new IllegalArgumentException("Connector ID must be between 1 and 50");
        }
        
        if (connector.getConnectorType() == null) {
            throw new IllegalArgumentException("Connector type is required");
        }
        
        if (connector.getStandard() == null) {
            throw new IllegalArgumentException("Connector standard is required");
        }
        
        if (connector.getPowerType() == null) {
            throw new IllegalArgumentException("Power type is required");
        }
    }

    private void validateConnectorBusinessRules(Connector connector) {
        // Validate reservation expiry
        if (connector.getReservationExpiresAt() != null && connector.getReservationId() == null) {
            throw new IllegalArgumentException("Reservation ID is required when reservation expiry is set");
        }
        
        // Validate transaction consistency
        if (connector.getCurrentTransactionId() != null) {
            if (!StringUtils.hasText(connector.getCurrentIdTag())) {
                throw new IllegalArgumentException("Current ID tag is required when transaction is active");
            }
        }
        
        // Validate pricing
        if (connector.getPricePerKwh() != null && connector.getPricePerKwh().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per kWh cannot be negative");
        }
        
        if (connector.getPricePerMinute() != null && connector.getPricePerMinute().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per minute cannot be negative");
        }
        
        if (connector.getPricePerSession() != null && connector.getPricePerSession().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per session cannot be negative");
        }
    }

    private void validateConnectorTechnicalSpecs(Connector connector) {
        // Validate power specifications
        if (connector.getMaxVoltage() != null) {
            if (connector.getMaxVoltage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Max voltage must be greater than 0");
            }
            if (connector.getMaxVoltage().compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new IllegalArgumentException("Max voltage cannot exceed 1000V");
            }
        }
        
        if (connector.getMaxAmperage() != null) {
            if (connector.getMaxAmperage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Max amperage must be greater than 0");
            }
            if (connector.getMaxAmperage().compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new IllegalArgumentException("Max amperage cannot exceed 1000A");
            }
        }
        
        if (connector.getMaxElectricPower() != null) {
            if (connector.getMaxElectricPower().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Max electric power must be greater than 0");
            }
            if (connector.getMaxElectricPower().compareTo(BigDecimal.valueOf(1000)) > 0) {
                throw new IllegalArgumentException("Max electric power cannot exceed 1000 kW");
            }
        }
        
        // Validate power type consistency
        validatePowerTypeConsistency(connector);
        
        // Validate meter readings
        if (connector.getTotalEnergyReadingKwh() != null && 
            connector.getTotalEnergyReadingKwh().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total energy reading cannot be negative");
        }
        
        if (connector.getLastMeterReadingKwh() != null && 
            connector.getLastMeterReadingKwh().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Last meter reading cannot be negative");
        }
    }

    private void validatePowerTypeConsistency(Connector connector) {
        // Validate connector type and power type consistency
        if (connector.getPowerType() == Connector.PowerType.DC) {
            // DC connectors should have appropriate types
            Connector.ConnectorType type = connector.getConnectorType();
            if (type != Connector.ConnectorType.CHADEMO && 
                type != Connector.ConnectorType.IEC_62196_T1_COMBO && 
                type != Connector.ConnectorType.IEC_62196_T2_COMBO &&
                type != Connector.ConnectorType.TESLA_SUPERCHARGER) {
                log.warn("Connector type {} may not be compatible with DC power type", type);
            }
        }
        
        // Validate voltage ranges for power types
        if (connector.getMaxVoltage() != null && connector.getPowerType() != null) {
            BigDecimal voltage = connector.getMaxVoltage();
            switch (connector.getPowerType()) {
                case AC_1_PHASE:
                    if (voltage.compareTo(BigDecimal.valueOf(300)) > 0) {
                        throw new IllegalArgumentException("AC single phase voltage typically should not exceed 300V");
                    }
                    break;
                case AC_3_PHASE:
                    if (voltage.compareTo(BigDecimal.valueOf(500)) > 0) {
                        throw new IllegalArgumentException("AC three phase voltage typically should not exceed 500V");
                    }
                    break;
                case DC:
                    if (voltage.compareTo(BigDecimal.valueOf(50)) < 0) {
                        log.warn("DC voltage below 50V is unusually low for EV charging");
                    }
                    break;
            }
        }
    }
}