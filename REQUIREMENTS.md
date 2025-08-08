# Open-CSMS - Requirements Document

**Version:** 1.0  
**Date:** 2025-01-07  
**Status:** Final

---

## Executive Summary

Open-CSMS is the first complete open source Charging Station Management System (CSMS) designed to compete with enterprise-grade proprietary solutions. Built with modern architecture and European standards compliance, it targets both direct CPO deployments and SaaS provider platforms.

### Key Differentiators
- **Complete OCPI 2.2.1 Hub capabilities** (unique in open source market)
- **Expert-grade charging station management** (all OCPP 2.0.1 parameters)
- **Maximum authentication compatibility** (RFID to ISO 15118)
- **Enterprise security with deployment simplicity**

---

## 1. Project Scope & Vision

### 1.1 Mission Statement
Create the first truly complete open source CSMS that rivals commercial solutions like Emabler, Last Mile Solutions, and Ocean (Etrel), while maintaining deployment simplicity and fostering an open ecosystem.

### 1.2 Target Market
- **Primary:** European CPO market (AFIR compliance required)
- **Direct CPOs:** Total Charging Services, Izivia, independent operators
- **SaaS CSMS providers:** Platform for multi-tenant SaaS offerings
- **System integrators:** Flexible foundation for custom solutions

### 1.3 Success Metrics
- Adoption by 10+ production CPO deployments within 18 months
- Active contribution from 5+ enterprise contributors
- OCPI hub processing 100K+ transactions monthly
- Migration path utilized by 3+ SteVe installations

---

## 2. Functional Requirements

### 2.1 Core MVP Features (Inspired by SteVe scope)

#### 2.1.1 Expert-Grade Charging Station Management
- **Complete OCPP 1.6 & 2.0.1 support** with backward compatibility
- **Advanced configuration capabilities:**
  - All OCPP 2.0.1 parameters accessible via UI/API
  - Smart charging profiles configuration
  - Display messages management
  - Certificate management (PKI integration)
  - Firmware OTA updates
  - Constructor-specific parameters
- **Multi-connector support:** Type2, CCS, CHAdeMO per station
- **Advanced diagnostics:**
  - Detailed hardware metrics collection
  - Real-time status monitoring
  - Predictive maintenance indicators
  - Complete logging with forensic capabilities

#### 2.1.2 Maximum Compatibility Authentication System
- **RFID traditional support:**
  - ISO14443 standard cards
  - Mifare technology compatibility
  - Bulk import/export capabilities
- **Modern authentication methods:**
  - NFC smartphone (badge emulation via mobile app)
  - Dynamic QR codes (app-generated, time-limited)
  - Plug & Charge ISO 15118 (vehicle-based authentication)
  - Autocharge (automatic vehicle recognition)
  - External API authentication (third-party system integration)
- **Advanced user management:**
  - Multi-level group management (enterprises, fleets, families)
  - Differentiated tarification by user type
  - Temporal and geographical restrictions
  - Roaming capabilities with other CPO networks

#### 2.1.3 Complete Telemetry Session Management
- **Real-time monitoring with complete telemetry:**
  - All OCPP metrics + constructor-specific custom metrics
  - Configurable measurement frequency (1 second to 1 minute)
  - Power curves, temperature monitoring, network diagnostics
  - Real-time alerts (anomalies, thresholds, predictive)
- **Robust session handling:**
  - Network interruption management with automatic recovery
  - Session resumption after incidents
  - Transaction integrity across network failures
- **Comprehensive billing support:**
  - Multi-criteria precision billing (kWh, time, peak power, dynamic tariffs)
  - Real-time cost calculation
  - Multiple billing models support
- **Data management:**
  - Intelligent long-term archival
  - Export capabilities (billing, analytics, compliance audit)
  - Forensic-grade transaction logging

### 2.2 Strategic Differentiation: OCPI 2.2.1 Hub

#### 2.2.1 Complete Hub Capabilities
- **OCPI 2.2.1 full specification implementation**
- **Hub functionality:** Enable interconnection of multiple CPOs
- **Core modules support:**
  - Locations management and real-time availability
  - Tariffs with complex pricing structures
  - Session management across networks
  - CDRs (Charge Detail Records) with full audit trail
  - Token management and validation
- **Advanced features:**
  - Smart charging coordination across networks
  - Reservation system with conflict resolution
  - Real-time tariff updates
  - Cross-network billing settlement
- **Business model enabler:** Allow SaaS providers to become OCPI hubs

---

## 3. Technical Architecture Requirements

### 3.1 Architecture Pattern
- **Modular hybrid architecture:** Core monolith + decoupled services
- **Core monolith:** Transactional consistency for sessions, billing, user management
- **Decoupled services:** Analytics, notifications, external integrations
- **Benefits:** Optimal balance between performance and operational simplicity

### 3.2 Technology Stack

#### 3.2.1 Backend Platform
- **Java/Spring Boot 3.x ecosystem**
- **Spring WebFlux** for reactive performance
- **Rationale:** 
  - Mature ecosystem with extensive OCPP/OCPI libraries
  - Large pool of qualified developers
  - Enterprise-grade performance and reliability
  - Conceptual compatibility with SteVe (easier migration path)

#### 3.2.2 Database Architecture - Polyglot Specialization
- **PostgreSQL:** Business data (CPO data, users, stations, sessions)
  - Multi-tenant isolation via tenant_id with optimized indexing
  - JSONB for flexible configuration storage
  - Full ACID compliance for financial transactions
- **InfluxDB:** Real-time telemetry and time-series data
  - Optimized for millions of measurement points per day
  - Efficient compression and retention policies
  - High-performance queries for analytics
- **Redis:** High-performance caching and session management
  - WebSocket OCPP session state management
  - Real-time data caching (<100ms latency requirement)
  - Distributed locking for concurrent operations
- **Elasticsearch (Optional):** Full-text search and audit logs
  - Advanced log analysis and forensic capabilities
  - Compliance reporting and audit trails

### 3.3 Multi-Tenant Architecture
- **Logical isolation model** using tenant_id in all tables
- **Benefits:** Optimal performance with operational simplicity
- **Security:** Application-level access control with row-level security
- **Scalability:** Supports both single-tenant and multi-tenant SaaS deployments

---

## 4. Integration & API Requirements

### 4.1 User Interfaces
- **Web application:** Modern SPA (React/Vue.js) for admin/CPO interface
- **Focus:** Clean, intuitive interface for complex operations
- **Mobile-responsive:** Tablet and mobile device support

### 4.2 External APIs
- **REST OCPI 2.2.1:** Complete hub implementation for interoperability
- **GraphQL API:** Flexible third-party integrations with schema introspection
- **WebSocket:** Real-time dashboard updates and live monitoring
- **Philosophy:** Backend-first with powerful APIs enabling ecosystem

### 4.3 Protocol Support
- **OCPP 1.6 (SOAP/JSON):** Backward compatibility with existing infrastructure
- **OCPP 2.0.1 (JSON):** Full specification support including security extensions
- **OCPI 2.2.1:** Complete implementation with hub capabilities
- **ISO 15118:** Plug & Charge integration

---

## 5. Security & Compliance Requirements

### 5.1 Authentication & Authorization
- **Flexible multi-provider support:**
  - OIDC/OAuth2 for modern SSO integration
  - SAML for enterprise directory integration
  - LDAP/Active Directory for on-premise environments
  - Cloud providers (Auth0, Keycloak, AWS Cognito, Azure AD)
- **Self-hosted options:** Complete sovereignty for sensitive deployments
- **MFA support:** Configurable (mandatory/optional) based on deployment requirements

### 5.2 Enterprise-Grade Security
- **OCPP/OCPI Security:**
  - TLS mutual authentication with comprehensive PKI certificate management
  - Granular API key management with tenant-scoped permissions
  - Advanced rate limiting with DDoS protection
  - Complete audit logging for compliance (GDPR/SOX ready)
- **Data Protection:**
  - Encryption at rest and in transit
  - Key rotation and secure key management
  - Data pseudonymization capabilities

### 5.3 European Compliance
- **GDPR by design:**
  - Data minimization and purpose limitation
  - Right to be forgotten with complete data removal
  - Privacy by design and by default
  - Consent management framework
- **Industry standards:**
  - ISO 27001 compatible architecture (certifiable)
  - SOC 2 compliance capabilities for SaaS providers
  - Cybersecurity Act (EU) readiness
- **AFIR compliance:** Full regulatory compliance for European market

---

## 6. Operational Requirements

### 6.1 Deployment Strategy
- **Docker Compose simplicity:** One-click deployment for small CPOs
- **Philosophy:** "Boring technology" - zero operational complexity
- **Components:**
  - Pre-configured docker-compose.yml with all services
  - Environment-based configuration (12-factor app principles)
  - Integrated backup automation with configurable retention
  - Built-in health checks with automatic service restart
  - Rolling updates with zero-downtime capability

### 6.2 Monitoring & Observability
- **Integrated dashboards:**
  - Business metrics (active sessions, revenue, station performance)
  - Technical metrics (latency, throughput, error rates)
  - Compliance metrics (uptime, response times, availability)
- **Essential alerting:**
  - Email and webhook notifications
  - Configurable thresholds for business and technical metrics
  - OCPP connectivity monitoring with automatic escalation
- **Real-time capabilities:**
  - Live OCPP/OCPI transaction monitoring
  - WebSocket-based dashboard updates
  - Centralized logging with search capabilities

### 6.3 Performance Requirements
- **Scalability targets:**
  - Support for 10,000+ simultaneous charging stations per instance
  - Handle 100,000+ daily charging sessions
  - <100ms response time for OCPP commands
  - <200ms response time for OCPI API calls
- **Availability:**
  - 99.9% uptime target (8.7 hours downtime/year)
  - Graceful degradation under load
  - Automatic failover capabilities

---

## 7. Business Model & Ecosystem

### 7.1 Open Source Strategy
- **License:** Apache 2.0 for maximum commercial adoption
- **Benefits:**
  - No restrictions on commercial usage
  - Encourages enterprise contributions without copyleft concerns
  - Compatible with commercial ecosystem (SaaS providers, system integrators)
  - Accelerates ecosystem growth and adoption

### 7.2 Business Model
- **Core open source:** All essential CSMS functionality freely available
- **Revenue streams:**
  - Professional support and consulting services
  - Managed hosting for small CPOs
  - Training and certification programs
  - Marketplace for plugins and extensions
- **Community-driven:** Sustainable through service ecosystem rather than licensing

### 7.3 Ecosystem Strategy
- **API-first approach:** Enable third-party tool development
- **Plugin architecture:** Allow community and commercial extensions
- **Integration partnerships:** Work with hardware manufacturers and service providers
- **Standards leadership:** Active participation in OCPP and OCPI evolution

---

## 8. Migration & Compatibility

### 8.1 SteVe Migration Path
- **Database migration tools:** Automated migration from SteVe installations
- **Configuration import:** Preserve existing station and user configurations
- **Minimal downtime:** Hot migration capabilities where possible
- **Validation tools:** Verify migration completeness and data integrity

### 8.2 Industry Integration
- **Hardware compatibility:** Works with all OCPP-compliant charging stations
- **Backend integration:** APIs for ERP, billing systems, payment processors
- **Standards compliance:** Full adherence to European and international standards

---

## 9. Success Criteria & Metrics

### 9.1 Adoption Metrics
- **Production deployments:** 10+ active CPO installations within 18 months
- **Transaction volume:** Process 1M+ charging transactions monthly
- **OCPI hub activity:** Facilitate 100K+ cross-network transactions monthly
- **Community engagement:** 5+ regular enterprise contributors

### 9.2 Technical Metrics
- **Performance:** Meet all latency and throughput requirements under load
- **Reliability:** Achieve 99.9% uptime in production environments
- **Security:** Zero critical security vulnerabilities
- **Compliance:** Pass all relevant European regulatory audits

### 9.3 Business Impact
- **Cost reduction:** 50-70% lower TCO vs proprietary solutions
- **Vendor independence:** Enable CPO independence from proprietary lock-in
- **Innovation acceleration:** Faster feature development through community contributions
- **Market expansion:** Enable new SaaS providers and system integrators

---

## 10. Development Priorities

### 10.1 Phase 1: Core MVP (Months 1-8)
1. **Foundation:** Multi-tenant architecture with PostgreSQL + Redis
2. **OCPP implementation:** Complete 1.6 and 2.0.1 support
3. **Basic UI:** Station management and session monitoring
4. **Authentication:** Flexible provider support with RBAC
5. **Deployment:** Docker Compose with monitoring

### 10.2 Phase 2: OCPI Hub (Months 6-12)
1. **OCPI 2.2.1:** Full specification implementation
2. **Hub capabilities:** Multi-CPO interconnection
3. **Advanced UI:** OCPI network management
4. **InfluxDB integration:** Complete telemetry system
5. **Security hardening:** PKI and advanced audit

### 10.3 Phase 3: Ecosystem (Months 10-18)
1. **GraphQL API:** Third-party integration platform
2. **Plugin system:** Community extension framework
3. **Advanced features:** Smart charging, predictive analytics
4. **Performance optimization:** Scale to 10K+ stations
5. **Community building:** Developer tools and documentation

---

## Appendices

### Appendix A: Competitive Analysis
[Detailed comparison with proprietary solutions]

### Appendix B: Technical Specifications
[Detailed API specifications and data models]

### Appendix C: Compliance Checklist
[Complete regulatory compliance requirements]

---

**Document Approval:**
- Requirements complete and validated
- Ready for architecture design phase
- Community review and feedback integration planned