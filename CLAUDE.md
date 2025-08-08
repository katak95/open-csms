# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Open-CSMS** is the first complete open source Charging Station Management System (CSMS) designed to compete with enterprise-grade proprietary solutions like Emabler, Last Mile Solutions, and Ocean (Etrel).

### Key Differentiators
- **Complete OCPI 2.2.1 Hub capabilities** (unique in open source market)
- **Expert-grade charging station management** (all OCPP 2.0.1 parameters)
- **Maximum authentication compatibility** (RFID to ISO 15118)
- **Enterprise security with deployment simplicity**

### Target Market
- **European CPO market** (AFIR compliance)
- **Direct CPOs**: Total Charging Services, Izivia, independent operators
- **SaaS CSMS providers**: Platform for multi-tenant SaaS offerings
- **System integrators**: Flexible foundation for custom solutions

## Architecture Summary

### Technology Stack
- **Backend**: Java 17 + Spring Boot 3.x + WebFlux
- **Database**: PostgreSQL (business) + InfluxDB (telemetry) + Redis (cache)
- **Architecture**: Modular hybrid (core monolith + decoupled services)
- **Deployment**: Docker Compose (boring ops philosophy)
- **License**: Apache 2.0 (maximum commercial adoption)

### Core Components
1. **Station Management** - OCPP 1.6/2.0.1 with expert-grade config
2. **Session Management** - Complete telemetry with billing precision  
3. **User & Auth** - Multi-provider auth (OIDC, SAML, LDAP)
4. **OCPI Hub** - 2.2.1 cross-network interoperability
5. **Multi-tenant** - Logical isolation with tenant_id

## Development Commands

### Build and Test
```bash
# Build application
./gradlew build

# Run tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Check test coverage (minimum 80%)
./gradlew jacocoTestReport

# Code quality checks
./gradlew check
./gradlew spotbugsMain
./gradlew pmdMain
```

### Database Management
```bash
# Start local PostgreSQL + InfluxDB + Redis
docker-compose -f docker/dev-compose.yml up -d

# Run database migrations
./gradlew flywayMigrate

# Reset database (development only)
./gradlew flywayClean flywayMigrate

# Generate test data
./gradlew loadTestData
```

### Local Development
```bash
# Start application in development mode
./gradlew bootRun --args='--spring.profiles.active=development'

# Start with debug enabled
./gradlew bootRun --debug-jvm

# Hot reload (with DevTools)
./gradlew bootRun -Pspring.devtools.restart.enabled=true
```

### Docker Development
```bash
# Build Docker image
docker build -t open-csms:dev .

# Run complete stack
docker-compose -f docker/full-stack.yml up

# View logs
docker-compose logs -f app

# Execute commands in container
docker-compose exec app bash
```

### Code Generation
```bash
# Generate JPA entities from schema
./gradlew generateEntities

# Generate OpenAPI client code
./gradlew openApiGenerate

# Generate GraphQL types
./gradlew generateGraphQL
```

## Architecture Guidelines

### Multi-Tenant Development
- **ALL entities** extend `TenantEntity` with `tenant_id` field
- **ALL repositories** use `@Query` with tenant filtering
- **ALL controllers** validate tenant access via `@RequiresTenantAccess`
- Use `TenantContextService` to get current tenant ID

### OCPP Implementation
- Handlers in `com.opencsms.ocpp.handlers` package
- Separate handlers for OCPP 1.6 and 2.0.1
- Use `@OCPPMessageHandler` annotation for auto-registration
- WebSocket session management via `OCPPSessionManager`

### OCPI Hub Implementation  
- Controllers in `com.opencsms.ocpi.controllers` package
- Hub logic in `OCPIHubService` for multi-CPO aggregation
- Partner management via `OCPIPartnerService`
- Cross-network session handling in `CrossNetworkSessionService`

### Security Implementation
- All endpoints require authentication (JWT tokens)
- Use `@PreAuthorize` with custom permission expressions
- PKI certificates stored in `CertificateManager`
- Multi-provider auth via `AuthenticationService`

### Database Patterns
```java
// Multi-tenant entity pattern
@Entity
@Table(name = "charging_stations")
public class ChargingStation extends TenantEntity {
    // Implementation
}

// Multi-tenant repository pattern  
@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {
    @Query("SELECT s FROM ChargingStation s WHERE s.tenantId = :tenantId AND s.stationId = :stationId")
    Optional<ChargingStation> findByTenantAndStationId(@Param("tenantId") UUID tenantId, 
                                                      @Param("stationId") String stationId);
}

// Service with tenant validation
@Service
public class ChargingStationService {
    
    @RequiresTenantAccess
    public ChargingStation createStation(CreateStationRequest request) {
        // Implementation with automatic tenant injection
    }
}
```

### API Development Patterns
```java
// REST Controller pattern
@RestController
@RequestMapping("/api/v1/stations")
@RequiresTenantAccess
public class StationController {
    
    @GetMapping("/{stationId}")
    @PreAuthorize("hasPermission('stations', 'read')")
    public ResponseEntity<StationDTO> getStation(@PathVariable String stationId) {
        // Implementation
    }
}

// GraphQL Resolver pattern
@Component
public class StationQueryResolver implements GraphQLQueryResolver {
    
    public ChargingStation station(String id) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        return stationService.findById(tenantId, UUID.fromString(id));
    }
}
```

## Key Files and Directories

### Core Application Structure
```
src/main/java/com/opencsms/
├── OpenCsmsApplication.java          # Main application class
├── config/                           # Configuration classes
│   ├── SecurityConfig.java          # Security configuration
│   ├── DatabaseConfig.java          # Multi-database setup
│   └── WebSocketConfig.java         # OCPP WebSocket config
├── domain/                           # Domain entities
│   ├── tenant/                      # Multi-tenant entities
│   ├── station/                     # Charging station domain
│   ├── session/                     # Charging session domain
│   ├── user/                        # User management domain
│   └── ocpi/                        # OCPI hub domain
├── service/                          # Business services
│   ├── StationService.java         # Station management
│   ├── SessionService.java         # Session lifecycle
│   ├── OCPIHubService.java         # OCPI hub logic
│   └── AuthenticationService.java  # Multi-provider auth
├── controller/                       # REST controllers
│   ├── api/v1/                     # Management APIs
│   ├── ocpp/                       # OCPP WebSocket handlers
│   ├── ocpi/                       # OCPI REST endpoints
│   └── graphql/                    # GraphQL resolvers
├── security/                         # Security components
│   ├── PKIManagementService.java   # Certificate management
│   ├── MultiTenantFilter.java      # Tenant isolation
│   └── PermissionService.java      # RBAC implementation
└── integration/                      # External integrations
    ├── ocpp/                        # OCPP protocol handling
    ├── ocpi/                        # OCPI protocol handling
    └── telemetry/                   # InfluxDB integration
```

### Configuration Files
```
src/main/resources/
├── application.yml                   # Main configuration
├── application-development.yml       # Dev environment
├── application-production.yml        # Production environment
├── db/migration/                     # Flyway database migrations
│   ├── V1__Initial_schema.sql       # Base schema
│   ├── V2__Multi_tenant_setup.sql   # Tenant isolation
│   └── V3__OCPI_tables.sql          # OCPI hub tables
├── graphql/                          # GraphQL schemas
│   ├── schema.graphqls              # Main schema
│   ├── station.graphqls             # Station types
│   └── session.graphqls             # Session types
└── certificates/                     # PKI certificates (dev only)
    ├── ca-cert.pem                  # Root CA
    └── server-cert.pem              # Server certificate
```

### Docker Configuration
```
docker/
├── Dockerfile                        # Production image
├── Dockerfile.dev                    # Development image
├── docker-compose.yml               # Production stack
├── dev-compose.yml                  # Development databases
└── full-stack.yml                   # Complete development stack
```

## Development Workflows

### Adding New Features
1. **Create feature branch**: `git checkout -b feature/charging-profiles`
2. **Add database migration**: Create new Flyway migration in `db/migration/`
3. **Create/update entities**: Extend `TenantEntity` for multi-tenant support
4. **Implement service logic**: Add business logic in appropriate service
5. **Add REST/GraphQL endpoints**: Create controllers/resolvers
6. **Write tests**: Unit tests + integration tests (minimum 80% coverage)
7. **Update API documentation**: OpenAPI specs for REST endpoints
8. **Create PR**: Include tests, documentation, and database migrations

### OCPP Message Handling
1. **Create message classes**: Request/Response POJOs in `ocpp.messages`
2. **Implement handler**: Extend `MessageHandler<Request, Response>`
3. **Add annotation**: Use `@OCPPMessageHandler(action = "ActionName", version = OCPPVersion.OCPP_2_0_1)`
4. **Register handler**: Auto-registered via component scan
5. **Add tests**: Mock WebSocket session and test message flow

### OCPI Endpoint Implementation  
1. **Define endpoint**: Create controller in `ocpi.controllers`
2. **Implement hub logic**: Add aggregation logic in `OCPIHubService`
3. **Add validation**: Request/response validation with proper error codes
4. **Partner authentication**: Validate OCPI tokens and permissions
5. **Add integration tests**: Test with mock OCPI partners

### Security Features
1. **Certificate management**: Use `PKIManagementService` for cert operations
2. **Authentication providers**: Implement `AuthenticationProvider` interface  
3. **Permission checks**: Add `@PreAuthorize` with custom permission expressions
4. **Audit logging**: Use `AuditService` for security event logging
5. **Encryption**: Use `EncryptionService` for sensitive data

## Testing Guidelines

### Unit Tests
- **Target**: 80% minimum code coverage
- **Pattern**: One test class per service/controller class
- **Mocking**: Use Mockito for dependencies, TestContainers for databases
- **Location**: `src/test/java/` mirroring main package structure

### Integration Tests
- **Target**: Critical business flows end-to-end
- **Pattern**: Full application context with TestContainers
- **Location**: `src/integrationTest/java/`
- **Databases**: Real PostgreSQL/InfluxDB/Redis via TestContainers

### OCPP Protocol Tests
- **WebSocket testing**: Mock WebSocket sessions
- **Message validation**: Test all OCPP 1.6 and 2.0.1 messages
- **Station simulation**: Use `OCPPStationSimulator` for testing

### OCPI Hub Tests  
- **Multi-partner scenarios**: Test hub aggregation logic
- **Cross-network sessions**: Test roaming session flows
- **Partner authentication**: Test OCPI token validation

## Performance Guidelines

### Database Performance
- **Indexes**: All tenant_id fields have composite indexes
- **Queries**: Use `@Query` with explicit JPA-QL for complex queries
- **Pagination**: Use Spring Data `Pageable` for large result sets
- **Caching**: Redis caching for frequently accessed data

### OCPP Performance
- **WebSocket connections**: Support 10K+ concurrent stations
- **Message queuing**: Redis-backed queues for reliable delivery
- **Session state**: Store session state in Redis for clustering
- **Heartbeat management**: Efficient heartbeat processing

### Multi-Tenant Performance
- **Row-Level Security**: PostgreSQL RLS for data isolation
- **Connection pooling**: Separate connection pools per tenant type
- **Caching strategy**: Tenant-aware Redis key patterns
- **Monitoring**: Per-tenant performance metrics

## Security Considerations

### Multi-Tenant Security
- **Data isolation**: Automatic tenant_id filtering on all queries
- **API security**: Tenant validation on all endpoints  
- **Certificate isolation**: Tenant-specific certificate stores
- **Audit logging**: All operations logged with tenant context

### OCPP Security
- **mTLS authentication**: Client certificates required for station connections
- **Certificate rotation**: Automated certificate lifecycle management
- **Security profiles**: OCPP 2.0.1 security profile support
- **Message encryption**: End-to-end encryption for sensitive data

### GDPR Compliance
- **Data minimization**: Only collect necessary personal data
- **Right to erasure**: User data pseudonymization functionality  
- **Data portability**: User data export in machine-readable format
- **Audit trail**: Complete audit log for compliance reporting

## Troubleshooting

### Common Issues
1. **Database connection errors**: Check database container status and connection strings
2. **WebSocket connection failures**: Verify certificate configuration and network connectivity  
3. **Multi-tenant isolation**: Ensure all queries include tenant_id filtering
4. **OCPI authentication**: Check partner token configuration and permissions
5. **Memory issues**: Monitor JVM heap usage and adjust container limits

### Development Tools
- **Database**: Use `pgAdmin` for PostgreSQL, `InfluxDB UI` for time-series data
- **API testing**: Use `Postman` collections in `tools/postman/`  
- **WebSocket testing**: Use `wscat` or custom OCPP simulator
- **Monitoring**: Access metrics at `/actuator/metrics` endpoint
- **Health checks**: Application health at `/actuator/health`

### Debugging
- **Enable debug logging**: Set `logging.level.com.opencsms=DEBUG` in application.yml
- **Database queries**: Enable `spring.jpa.show-sql=true` for SQL logging  
- **OCPP messages**: Enable WebSocket message logging in `OCPPWebSocketHandler`
- **Security events**: Monitor audit logs in `security_events` table

## Deployment Notes

### Environment Variables
```bash
# Database configuration
DATABASE_URL=jdbc:postgresql://postgres:5432/opencsms
DATABASE_USERNAME=opencsms
DATABASE_PASSWORD=<secure-password>

# Redis configuration  
REDIS_URL=redis://redis:6379
REDIS_PASSWORD=<secure-password>

# InfluxDB configuration
INFLUXDB_URL=http://influxdb:8086
INFLUXDB_TOKEN=<secure-token>
INFLUXDB_ORG=opencsms
INFLUXDB_BUCKET=telemetry

# Security configuration
JWT_SECRET=<256-bit-secret>
ENCRYPTION_KEY=<256-bit-key>

# OCPP configuration
OCPP_WEBSOCKET_PORT=8443
OCPP_TLS_KEYSTORE_PATH=/app/certs/keystore.p12
OCPP_TLS_KEYSTORE_PASSWORD=<keystore-password>

# OCPI configuration  
OCPI_HUB_MODE=true
OCPI_COUNTRY_CODE=FR
OCPI_PARTY_ID=OCS
```

### Production Checklist
- [ ] Database migrations applied (`./gradlew flywayMigrate`)
- [ ] SSL certificates configured and valid
- [ ] Environment variables set correctly  
- [ ] Health checks responding (`/actuator/health`)
- [ ] Monitoring and alerting configured
- [ ] Backup strategy implemented
- [ ] Security scanning completed
- [ ] Load testing performed
- [ ] Documentation updated

## Documentation

### API Documentation
- **REST APIs**: OpenAPI 3.0 specs at `/swagger-ui/`
- **GraphQL**: Schema browser at `/graphiql`  
- **OCPP**: Protocol documentation in `docs/ocpp/`
- **OCPI**: Hub implementation guide in `docs/ocpi/`

### Architecture Documentation
- **System overview**: `docs/architecture/system-overview.md`
- **Database schema**: `docs/database/schema-documentation.md`
- **Security design**: `docs/security/security-architecture.md`
- **Deployment guide**: `docs/deployment/production-deployment.md`

This project represents a complete, enterprise-grade CSMS implementation with modern architecture patterns, comprehensive security, and production-ready deployment capabilities.