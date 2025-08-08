-- V001: Create tenant schema and tables
-- Multi-tenant architecture foundation

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create tenants table (not multi-tenant, global table)
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    description TEXT,
    tenant_type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    trial_ends_at TIMESTAMP WITH TIME ZONE,
    
    -- Configuration embedded fields
    timezone VARCHAR(100) DEFAULT 'UTC',
    currency VARCHAR(3) DEFAULT 'EUR',
    language VARCHAR(5) DEFAULT 'en',
    date_format VARCHAR(50) DEFAULT 'yyyy-MM-dd',
    time_format VARCHAR(50) DEFAULT 'HH:mm:ss',
    max_stations INTEGER,
    max_connectors INTEGER,
    max_users INTEGER,
    max_sessions_per_month INTEGER,
    max_energy_per_month_kwh BIGINT,
    session_timeout_minutes INTEGER DEFAULT 240,
    idle_timeout_minutes INTEGER DEFAULT 30,
    max_power_kw INTEGER,
    allow_remote_start BOOLEAN DEFAULT true,
    allow_remote_stop BOOLEAN DEFAULT true,
    require_authorization BOOLEAN DEFAULT true,
    allow_guest_charging BOOLEAN DEFAULT false,
    auto_stop_on_disconnect BOOLEAN DEFAULT true,
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(255),
    api_rate_limit_per_minute INTEGER DEFAULT 600,
    logo_url VARCHAR(500),
    primary_color VARCHAR(7) DEFAULT '#1976D2',
    secondary_color VARCHAR(7) DEFAULT '#DC004E',
    
    -- Contact embedded fields
    contact_name VARCHAR(200),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    company_name VARCHAR(200),
    company_vat VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(2),
    website VARCHAR(255),
    support_email VARCHAR(255),
    support_phone VARCHAR(50),
    emergency_phone VARCHAR(50),
    
    -- Billing embedded fields
    billing_plan VARCHAR(50) DEFAULT 'FREE',
    billing_cycle VARCHAR(50) DEFAULT 'MONTHLY',
    billing_email VARCHAR(255),
    billing_address_line1 VARCHAR(255),
    billing_address_line2 VARCHAR(255),
    billing_city VARCHAR(100),
    billing_state_province VARCHAR(100),
    billing_postal_code VARCHAR(20),
    billing_country VARCHAR(2),
    payment_method VARCHAR(50),
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    monthly_rate DECIMAL(10,2),
    per_session_rate DECIMAL(10,4),
    per_kwh_rate DECIMAL(10,4),
    credit_balance DECIMAL(10,2) DEFAULT 0,
    credit_limit DECIMAL(10,2),
    next_billing_date DATE,
    invoice_prefix VARCHAR(20),
    last_invoice_number BIGINT DEFAULT 0,
    tax_rate DECIMAL(5,2),
    tax_number VARCHAR(50),
    purchase_order_number VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    suspended_at TIMESTAMP WITH TIME ZONE,
    suspension_reason VARCHAR(500)
);

-- Create indexes for tenants table
CREATE INDEX idx_tenant_code ON tenants(code);
CREATE INDEX idx_tenant_active ON tenants(active);
CREATE INDEX idx_tenant_type ON tenants(tenant_type);
CREATE INDEX idx_tenant_created_at ON tenants(created_at);

-- Create tenant features table
CREATE TABLE tenant_features (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feature VARCHAR(50) NOT NULL,
    PRIMARY KEY (tenant_id, feature)
);

CREATE INDEX idx_tenant_features_tenant ON tenant_features(tenant_id);
CREATE INDEX idx_tenant_features_feature ON tenant_features(feature);

-- Create tenant metadata table
CREATE TABLE tenant_metadata (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key VARCHAR(255) NOT NULL,
    value TEXT,
    PRIMARY KEY (tenant_id, key)
);

CREATE INDEX idx_tenant_metadata_tenant ON tenant_metadata(tenant_id);
CREATE INDEX idx_tenant_metadata_key ON tenant_metadata(key);

-- Create audit log table (global, not tenant-specific)
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50),
    user_id UUID,
    username VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    old_values JSONB,
    new_values JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- Create system_settings table (global configuration)
CREATE TABLE system_settings (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT,
    description TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

-- Insert default system settings
INSERT INTO system_settings (key, value, description) VALUES
    ('system.version', '0.1.0', 'System version'),
    ('system.maintenance_mode', 'false', 'Maintenance mode flag'),
    ('system.max_tenants', '1000', 'Maximum number of tenants'),
    ('system.default_trial_days', '30', 'Default trial period in days'),
    ('system.require_email_verification', 'true', 'Require email verification for new users'),
    ('system.password_min_length', '8', 'Minimum password length'),
    ('system.session_timeout_minutes', '60', 'Default session timeout'),
    ('system.max_failed_login_attempts', '5', 'Maximum failed login attempts before lockout'),
    ('system.lockout_duration_minutes', '15', 'Account lockout duration');

-- Create sample tenant for development
INSERT INTO tenants (
    code, 
    name, 
    display_name, 
    description, 
    tenant_type,
    contact_email,
    company_name,
    country
) VALUES (
    'demo',
    'Demo Tenant',
    'Open-CSMS Demo',
    'Demo tenant for development and testing',
    'DEMO',
    'demo@opencsms.io',
    'Open-CSMS Demo Company',
    'FR'
);

-- Add demo tenant features
INSERT INTO tenant_features (tenant_id, feature) 
SELECT id, feature FROM tenants, 
    (VALUES 
        ('OCPP_1_6'),
        ('OCPP_2_0_1'),
        ('OCPI_2_2_1'),
        ('SMART_CHARGING'),
        ('ANALYTICS_ADVANCED'),
        ('API_ACCESS')
    ) AS features(feature)
WHERE code = 'demo';

-- Function to set updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for tenants table
CREATE TRIGGER update_tenants_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();