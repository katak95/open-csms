-- V002: Create user and authentication schema
-- Multi-tenant user management

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(200),
    phone VARCHAR(50),
    mobile VARCHAR(50),
    
    -- Authentication fields
    email_verified BOOLEAN DEFAULT false,
    email_verification_token VARCHAR(255),
    email_verification_sent_at TIMESTAMP WITH TIME ZONE,
    password_reset_token VARCHAR(255),
    password_reset_sent_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_login_ip VARCHAR(45),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    
    -- User preferences
    language VARCHAR(5) DEFAULT 'en',
    timezone VARCHAR(100) DEFAULT 'UTC',
    date_format VARCHAR(50),
    time_format VARCHAR(50),
    notifications_enabled BOOLEAN DEFAULT true,
    
    -- Status
    active BOOLEAN DEFAULT true,
    suspended BOOLEAN DEFAULT false,
    suspension_reason VARCHAR(500),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    
    CONSTRAINT uk_users_username_tenant UNIQUE (username, tenant_id),
    CONSTRAINT uk_users_email_tenant UNIQUE (email, tenant_id)
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(active);
CREATE INDEX idx_users_deleted ON users(deleted);

-- Create roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    system_role BOOLEAN DEFAULT false,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    
    CONSTRAINT uk_roles_code_tenant UNIQUE (code, tenant_id)
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);
CREATE INDEX idx_roles_code ON roles(code);
CREATE INDEX idx_roles_system ON roles(system_role);

-- Create permissions table
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    CONSTRAINT uk_permissions_resource_action UNIQUE (resource, action)
);

CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);

-- Create role_permissions junction table
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

-- Create user_roles junction table
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(255),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

-- Create auth_tokens table (for RFID cards, mobile apps, etc.)
CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token_type VARCHAR(50) NOT NULL, -- RFID, MOBILE_APP, API_KEY
    token_value VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255),
    name VARCHAR(200),
    description TEXT,
    
    -- Validity
    valid_from TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count BIGINT DEFAULT 0,
    
    -- Status
    active BOOLEAN DEFAULT true,
    blocked BOOLEAN DEFAULT false,
    block_reason VARCHAR(500),
    
    -- Metadata
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    
    CONSTRAINT uk_auth_tokens_value_tenant UNIQUE (token_value, tenant_id)
);

CREATE INDEX idx_auth_tokens_tenant ON auth_tokens(tenant_id);
CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id);
CREATE INDEX idx_auth_tokens_type ON auth_tokens(token_type);
CREATE INDEX idx_auth_tokens_value ON auth_tokens(token_value);
CREATE INDEX idx_auth_tokens_active ON auth_tokens(active);

-- Create sessions table (for web sessions)
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255),
    
    -- Session details
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_type VARCHAR(50),
    device_id VARCHAR(255),
    
    -- Validity
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Status
    active BOOLEAN DEFAULT true,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by VARCHAR(255),
    revoke_reason VARCHAR(500)
);

CREATE INDEX idx_user_sessions_tenant ON user_sessions(tenant_id);
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_token ON user_sessions(session_token);
CREATE INDEX idx_user_sessions_refresh ON user_sessions(refresh_token);
CREATE INDEX idx_user_sessions_active ON user_sessions(active);
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at);

-- Insert default permissions
INSERT INTO permissions (resource, action, name, description) VALUES
    -- Station management
    ('stations', 'create', 'Create Stations', 'Create new charging stations'),
    ('stations', 'read', 'View Stations', 'View charging stations'),
    ('stations', 'update', 'Update Stations', 'Update charging station configuration'),
    ('stations', 'delete', 'Delete Stations', 'Delete charging stations'),
    ('stations', 'control', 'Control Stations', 'Send commands to stations'),
    
    -- Session management
    ('sessions', 'create', 'Start Sessions', 'Start charging sessions'),
    ('sessions', 'read', 'View Sessions', 'View charging sessions'),
    ('sessions', 'stop', 'Stop Sessions', 'Stop charging sessions'),
    ('sessions', 'export', 'Export Sessions', 'Export session data'),
    
    -- User management
    ('users', 'create', 'Create Users', 'Create new users'),
    ('users', 'read', 'View Users', 'View user information'),
    ('users', 'update', 'Update Users', 'Update user information'),
    ('users', 'delete', 'Delete Users', 'Delete users'),
    
    -- Token management
    ('tokens', 'create', 'Create Tokens', 'Create authentication tokens'),
    ('tokens', 'read', 'View Tokens', 'View authentication tokens'),
    ('tokens', 'update', 'Update Tokens', 'Update token configuration'),
    ('tokens', 'delete', 'Delete Tokens', 'Delete authentication tokens'),
    
    -- Analytics
    ('analytics', 'view', 'View Analytics', 'View analytics and reports'),
    ('analytics', 'export', 'Export Analytics', 'Export analytics data'),
    
    -- Settings
    ('settings', 'read', 'View Settings', 'View system settings'),
    ('settings', 'update', 'Update Settings', 'Update system settings'),
    
    -- Billing
    ('billing', 'read', 'View Billing', 'View billing information'),
    ('billing', 'manage', 'Manage Billing', 'Manage billing and payments'),
    
    -- OCPI
    ('ocpi', 'manage', 'Manage OCPI', 'Manage OCPI connections and partners');

-- Insert default roles for demo tenant
INSERT INTO roles (tenant_id, code, name, description, system_role) VALUES
    ('demo', 'SUPER_ADMIN', 'Super Administrator', 'Full system access', true),
    ('demo', 'ADMIN', 'Administrator', 'Tenant administration', true),
    ('demo', 'OPERATOR', 'Operator', 'Station and session management', true),
    ('demo', 'USER', 'User', 'Basic user access', true),
    ('demo', 'VIEWER', 'Viewer', 'Read-only access', true);

-- Assign all permissions to SUPER_ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.code = 'SUPER_ADMIN' AND r.tenant_id = 'demo';

-- Assign specific permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND r.tenant_id = 'demo'
    AND p.resource IN ('stations', 'sessions', 'users', 'tokens', 'analytics', 'settings');

-- Assign specific permissions to OPERATOR role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.code = 'OPERATOR' AND r.tenant_id = 'demo'
    AND (
        (p.resource = 'stations' AND p.action IN ('read', 'update', 'control')) OR
        (p.resource = 'sessions' AND p.action IN ('create', 'read', 'stop')) OR
        (p.resource = 'tokens' AND p.action IN ('read')) OR
        (p.resource = 'analytics' AND p.action = 'view')
    );

-- Assign specific permissions to USER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.code = 'USER' AND r.tenant_id = 'demo'
    AND (
        (p.resource = 'sessions' AND p.action IN ('create', 'read', 'stop')) OR
        (p.resource = 'tokens' AND p.action IN ('read'))
    );

-- Assign specific permissions to VIEWER role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.code = 'VIEWER' AND r.tenant_id = 'demo'
    AND p.action = 'read';

-- Create demo admin user (password: Admin123!)
INSERT INTO users (
    tenant_id,
    username,
    email,
    password_hash,
    first_name,
    last_name,
    display_name,
    email_verified,
    active
) VALUES (
    'demo',
    'admin',
    'admin@opencsms.io',
    '$2a$10$YKrJmVVpCR1zrBwVdcVjPuLKjYXqyKGFNgO.Qa5EBQHeI/u5TLHHK', -- Admin123!
    'Admin',
    'User',
    'Administrator',
    true,
    true
);

-- Assign SUPER_ADMIN role to demo admin user
INSERT INTO user_roles (user_id, role_id, granted_by)
SELECT u.id, r.id, 'system'
FROM users u, roles r
WHERE u.username = 'admin' AND u.tenant_id = 'demo'
    AND r.code = 'SUPER_ADMIN' AND r.tenant_id = 'demo';

-- Add triggers for updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auth_tokens_updated_at BEFORE UPDATE ON auth_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();