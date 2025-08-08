-- Open-CSMS Development Database Initialization
-- This script sets up the development database with basic configuration

-- Create development user and database (if not exists)
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'opencsms_dev') THEN

      CREATE ROLE opencsms_dev LOGIN PASSWORD 'opencsms_dev_password';
   END IF;
END
$do$;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE opencsms_dev TO opencsms_dev;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Set timezone
SET TIME ZONE 'UTC';

-- Development-specific settings
ALTER DATABASE opencsms_dev SET log_statement = 'all';
ALTER DATABASE opencsms_dev SET log_duration = 'on';

-- Create schemas for multi-tenant architecture
CREATE SCHEMA IF NOT EXISTS public;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS telemetry;

-- Grant schema permissions
GRANT ALL ON SCHEMA public TO opencsms_dev;
GRANT ALL ON SCHEMA audit TO opencsms_dev;
GRANT ALL ON SCHEMA telemetry TO opencsms_dev;

-- Create application user for connection pooling
CREATE ROLE application_role;
GRANT CONNECT ON DATABASE opencsms_dev TO application_role;
GRANT USAGE ON SCHEMA public, audit, telemetry TO application_role;

-- Development seed data will be added by Flyway migrations