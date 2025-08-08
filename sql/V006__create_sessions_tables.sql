-- V006__create_sessions_tables.sql
-- Create tables for charging sessions, meter values, tariffs, and related entities

-- Create tariffs table
CREATE TABLE tariffs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    tariff_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    
    -- Simple pricing
    price_per_kwh DECIMAL(10,4),
    price_per_minute DECIMAL(10,4),
    price_per_hour DECIMAL(10,4),
    service_fee DECIMAL(10,2),
    connection_fee DECIMAL(10,2),
    
    -- Validity period
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    
    -- Time restrictions
    start_time TIME,
    end_time TIME,
    days_of_week VARCHAR(20),
    
    -- Minimums and maximums
    min_charging_amount_kwh DECIMAL(10,3),
    max_charging_amount_kwh DECIMAL(10,3),
    min_session_duration_minutes INTEGER,
    max_session_duration_minutes INTEGER,
    
    -- Power-based pricing
    price_per_kw_slow DECIMAL(10,4),
    price_per_kw_fast DECIMAL(10,4),
    price_per_kw_rapid DECIMAL(10,4),
    
    -- Status and configuration
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_tariff BOOLEAN NOT NULL DEFAULT FALSE,
    public_tariff BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Advanced configuration
    applicable_stations TEXT,
    applicable_connector_types VARCHAR(200),
    user_types VARCHAR(100),
    membership_required BOOLEAN DEFAULT FALSE,
    billing_increment_seconds INTEGER DEFAULT 60,
    billing_increment_kwh DECIMAL(5,3) DEFAULT 0.001,
    
    -- Tax information
    tax_rate DECIMAL(5,4),
    tax_included BOOLEAN DEFAULT TRUE,
    
    -- Metadata
    tags VARCHAR(500),
    external_tariff_id VARCHAR(100),
    version INTEGER NOT NULL DEFAULT 1,
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_tariff_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_tariff_tenant_external_id UNIQUE (tenant_id, external_tariff_id),
    CONSTRAINT ck_tariff_currency_length CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_tariff_prices_positive CHECK (
        (price_per_kwh IS NULL OR price_per_kwh >= 0) AND
        (price_per_minute IS NULL OR price_per_minute >= 0) AND
        (price_per_hour IS NULL OR price_per_hour >= 0)
    )
);

-- Create tariff elements table for complex pricing
CREATE TABLE tariff_elements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    tariff_id UUID NOT NULL REFERENCES tariffs(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    price_component VARCHAR(20) NOT NULL,
    price DECIMAL(10,4) NOT NULL,
    step_size VARCHAR(20),
    
    -- Thresholds
    min_value DECIMAL(15,3),
    max_value DECIMAL(15,3),
    
    -- Time restrictions
    start_time VARCHAR(8),
    end_time VARCHAR(8),
    min_duration_seconds INTEGER,
    max_duration_seconds INTEGER,
    days_mask INTEGER DEFAULT 127,
    
    -- Additional configuration
    billing_increment DECIMAL(10,3),
    description VARCHAR(500),
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_tariff_element_price_positive CHECK (price >= 0),
    CONSTRAINT ck_tariff_element_sequence_positive CHECK (sequence_number > 0),
    CONSTRAINT ck_tariff_element_values CHECK (
        min_value IS NULL OR max_value IS NULL OR min_value < max_value
    )
);

-- Create charging sessions table
CREATE TABLE charging_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    session_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    
    -- User and authentication
    user_id UUID REFERENCES users(id),
    auth_token_id UUID REFERENCES auth_tokens(id),
    
    -- Station and connector
    station_id UUID NOT NULL,
    station_serial VARCHAR(100),
    connector_id UUID NOT NULL REFERENCES connectors(id),
    connector_number INTEGER NOT NULL,
    
    -- Session status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- OCPP transaction details
    ocpp_transaction_id INTEGER,
    ocpp_id_tag VARCHAR(50),
    
    -- Time information
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    authorization_time TIMESTAMPTZ,
    
    -- Meter readings
    meter_start DECIMAL(15,3),
    meter_stop DECIMAL(15,3),
    energy_delivered_kwh DECIMAL(15,3),
    duration_minutes INTEGER,
    max_power_kw DECIMAL(10,3),
    average_power_kw DECIMAL(10,3),
    
    -- Stop information
    stop_reason VARCHAR(30),
    failure_reason VARCHAR(500),
    
    -- Pricing and billing
    tariff_id UUID REFERENCES tariffs(id),
    currency CHAR(3) DEFAULT 'EUR',
    price_per_kwh DECIMAL(10,4),
    price_per_minute DECIMAL(10,4),
    session_cost DECIMAL(10,2),
    energy_cost DECIMAL(10,2),
    time_cost DECIMAL(10,2),
    service_fee DECIMAL(10,2),
    total_cost DECIMAL(10,2),
    
    -- Vehicle information
    vehicle_id VARCHAR(50),
    license_plate VARCHAR(20),
    
    -- Remote control
    remote_start BOOLEAN DEFAULT FALSE,
    remote_stop BOOLEAN DEFAULT FALSE,
    remote_stop_requested TIMESTAMPTZ,
    
    -- Reservation and roaming
    reservation_id UUID,
    ocpi_session_id VARCHAR(36),
    roaming_session BOOLEAN DEFAULT FALSE,
    partner_name VARCHAR(100),
    
    -- Additional metadata
    notes TEXT,
    error_code VARCHAR(50),
    charging_profile_id UUID,
    max_charging_rate_kw DECIMAL(10,3),
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uq_session_uuid UNIQUE (session_uuid),
    CONSTRAINT uq_session_ocpp_transaction UNIQUE (tenant_id, ocpp_transaction_id),
    CONSTRAINT ck_session_currency_length CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_session_meter_values CHECK (
        meter_start IS NULL OR meter_stop IS NULL OR meter_stop >= meter_start
    ),
    CONSTRAINT ck_session_times CHECK (
        start_time IS NULL OR end_time IS NULL OR end_time >= start_time
    ),
    CONSTRAINT ck_session_costs_positive CHECK (
        (session_cost IS NULL OR session_cost >= 0) AND
        (energy_cost IS NULL OR energy_cost >= 0) AND
        (time_cost IS NULL OR time_cost >= 0) AND
        (service_fee IS NULL OR service_fee >= 0) AND
        (total_cost IS NULL OR total_cost >= 0)
    )
);

-- Create session status history table
CREATE TABLE session_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    session_id UUID NOT NULL REFERENCES charging_sessions(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    triggered_by VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create meter values table
CREATE TABLE meter_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    session_id UUID NOT NULL REFERENCES charging_sessions(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    measurand VARCHAR(40) NOT NULL,
    value DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20),
    context VARCHAR(30),
    location VARCHAR(20),
    phase VARCHAR(10),
    
    -- Calculated convenience fields
    energy_kwh DECIMAL(15,3),
    power_kw DECIMAL(10,3),
    current_a DECIMAL(10,2),
    voltage_v DECIMAL(10,2),
    soc_percent DECIMAL(5,2),
    temperature_c DECIMAL(5,2),
    
    -- Audit fields
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for tariffs
CREATE INDEX idx_tariff_tenant_id ON tariffs(tenant_id);
CREATE INDEX idx_tariff_code ON tariffs(code);
CREATE INDEX idx_tariff_valid_dates ON tariffs(valid_from, valid_until);
CREATE INDEX idx_tariff_active ON tariffs(active);
CREATE INDEX idx_tariff_default ON tariffs(default_tariff);

-- Create indexes for tariff elements
CREATE INDEX idx_element_tariff_id ON tariff_elements(tariff_id);
CREATE INDEX idx_element_sequence ON tariff_elements(sequence_number);

-- Create indexes for charging sessions
CREATE INDEX idx_session_tenant_id ON charging_sessions(tenant_id);
CREATE INDEX idx_session_user_id ON charging_sessions(user_id);
CREATE INDEX idx_session_station_id ON charging_sessions(station_id);
CREATE INDEX idx_session_connector_id ON charging_sessions(connector_id);
CREATE INDEX idx_session_status ON charging_sessions(status);
CREATE INDEX idx_session_start_time ON charging_sessions(start_time);
CREATE INDEX idx_session_transaction_id ON charging_sessions(ocpp_transaction_id);
CREATE INDEX idx_session_created_at ON charging_sessions(created_at);

-- Create indexes for session status history
CREATE INDEX idx_status_history_session ON session_status_history(session_id);
CREATE INDEX idx_status_history_timestamp ON session_status_history(timestamp);

-- Create indexes for meter values
CREATE INDEX idx_meter_session_id ON meter_values(session_id);
CREATE INDEX idx_meter_timestamp ON meter_values(timestamp);
CREATE INDEX idx_meter_measurand ON meter_values(measurand);

-- Add updated_at trigger for tariffs
CREATE TRIGGER trigger_tariffs_updated_at 
    BEFORE UPDATE ON tariffs 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add updated_at trigger for tariff elements
CREATE TRIGGER trigger_tariff_elements_updated_at 
    BEFORE UPDATE ON tariff_elements 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add updated_at trigger for charging sessions
CREATE TRIGGER trigger_charging_sessions_updated_at 
    BEFORE UPDATE ON charging_sessions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add updated_at trigger for session status history
CREATE TRIGGER trigger_session_status_history_updated_at 
    BEFORE UPDATE ON session_status_history 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add updated_at trigger for meter values
CREATE TRIGGER trigger_meter_values_updated_at 
    BEFORE UPDATE ON meter_values 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create session statistics view
CREATE VIEW session_statistics AS
SELECT 
    s.tenant_id,
    COUNT(*) as total_sessions,
    COUNT(*) FILTER (WHERE s.status = 'COMPLETED') as completed_sessions,
    COUNT(*) FILTER (WHERE s.status = 'FAILED') as failed_sessions,
    COUNT(*) FILTER (WHERE s.status IN ('CHARGING', 'SUSPENDED_EV', 'SUSPENDED_EVSE')) as active_sessions,
    COALESCE(SUM(s.energy_delivered_kwh), 0) as total_energy_kwh,
    COALESCE(AVG(s.duration_minutes), 0) as avg_duration_minutes,
    COALESCE(SUM(s.total_cost), 0) as total_revenue,
    COALESCE(AVG(s.total_cost), 0) as avg_session_cost,
    DATE_TRUNC('day', CURRENT_TIMESTAMP) as calculation_date
FROM charging_sessions s
WHERE s.start_time >= CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY s.tenant_id;

-- Insert default tariff for demo tenant
INSERT INTO tariffs (
    tenant_id, 
    code, 
    name, 
    description, 
    tariff_type,
    currency,
    price_per_kwh,
    price_per_minute,
    service_fee,
    active,
    default_tariff,
    public_tariff
) VALUES (
    'demo',
    'DEFAULT',
    'Default Tariff',
    'Standard pricing for charging sessions',
    'SIMPLE',
    'EUR',
    0.30,
    0.02,
    0.00,
    true,
    true,
    true
);

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON tariffs TO opencsms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tariff_elements TO opencsms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON charging_sessions TO opencsms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON session_status_history TO opencsms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON meter_values TO opencsms_app;
GRANT SELECT ON session_statistics TO opencsms_app;