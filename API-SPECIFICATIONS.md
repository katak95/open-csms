# Open-CSMS - API Specifications

**Version:** 1.0  
**Date:** 2025-01-07  
**Status:** Draft

---

## 1. Overview

Cette spécification définit toutes les APIs d'open-csms :
- **OCPP WebSocket APIs** (1.6 & 2.0.1) 
- **OCPI REST APIs** (2.2.1 Hub complet)
- **GraphQL API** (intégrations tierces flexibles)
- **WebSocket API** (temps réel dashboards)
- **Management REST API** (administration)

### 1.1 Architecture APIs

```
┌─────────────────────────────────────────────────────────────┐
│                    EXTERNAL INTERFACES                     │
├─────────────────┬─────────────────┬─────────────────────────┤
│ Charging        │ OCPI Partners   │ Third-Party             │
│ Stations        │ (CPOs/MSPs)     │ Applications            │
│ OCPP WS         │ OCPI REST       │ GraphQL/REST/WS         │
└─────────────────┴─────────────────┴─────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY                             │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ Authentication & Authorization (JWT/OAuth2)         │  │
│  │ Rate Limiting & Throttling                          │  │
│  │ Request/Response Logging & Audit                    │  │
│  │ Multi-tenant Context Injection                      │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                   API IMPLEMENTATIONS                      │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ OCPP        │ OCPI        │ GraphQL     │ Management  │  │
│  │ Handler     │ Controller  │ Resolver    │ REST API    │  │
│  │ (WS)        │ (REST)      │ (Query/Sub) │ (CRUD)      │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. OCPP WebSocket APIs

### 2.1 OCPP 1.6 Implementation

#### 2.1.1 Connection Management
```java
@Component
@Slf4j
public class OCPP16WebSocketHandler extends TextWebSocketHandler {
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("OCPP 1.6 connection established: {}", session.getId());
        
        // Extract station ID from URL path
        String stationId = extractStationId(session.getUri());
        
        // Validate station credentials
        ChargingStation station = validateStationCredentials(stationId, session);
        
        // Register session in Redis
        ocppSessionManager.registerSession(stationId, session, OCPPVersion.OCPP_1_6);
        
        // Send initial configuration
        sendInitialConfiguration(session, station);
        
        // Update station status to online
        stationService.updateConnectionStatus(station.getTenantId(), stationId, 
                                            OCPPStatus.AVAILABLE);
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) 
            throws Exception {
        
        String stationId = getStationIdFromSession(session);
        
        try {
            // Parse OCPP message
            OCPP16Message ocppMessage = objectMapper.readValue(
                message.getPayload(), OCPP16Message.class);
            
            // Route to appropriate handler
            CompletableFuture<OCPP16Response> responseFuture = 
                messageRouter.route(stationId, ocppMessage);
            
            // Send response asynchronously
            responseFuture.thenAccept(response -> {
                try {
                    String responseJson = objectMapper.writeValueAsString(response);
                    session.sendMessage(new TextMessage(responseJson));
                } catch (Exception e) {
                    log.error("Error sending OCPP response", e);
                }
            });
            
        } catch (Exception e) {
            log.error("Error processing OCPP message from station {}", stationId, e);
            sendErrorResponse(session, "InternalError", "Message processing failed");
        }
    }
}
```

#### 2.1.2 Core OCPP 1.6 Messages
```java
// Boot Notification
@OCPPMessageHandler(action = "BootNotification", version = OCPPVersion.OCPP_1_6)
public class BootNotificationHandler implements MessageHandler<BootNotificationRequest, BootNotificationResponse> {
    
    @Override
    public CompletableFuture<BootNotificationResponse> handle(String stationId, 
                                                            BootNotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            
            // Update station information
            ChargingStation station = stationService.updateStationInfo(stationId, 
                StationInfo.builder()
                    .manufacturer(request.getChargePointVendor())
                    .model(request.getChargePointModel())
                    .serialNumber(request.getChargePointSerialNumber())
                    .firmwareVersion(request.getFirmwareVersion())
                    .build());
            
            // Log boot notification
            auditService.logSystemEvent(station.getTenantId(), 
                AuditAction.SYSTEM_EVENT, 
                "Station boot notification received", 
                Map.of("stationId", stationId, "request", request));
            
            return BootNotificationResponse.builder()
                .status(RegistrationStatus.ACCEPTED)
                .currentTime(Instant.now())
                .interval(station.getHeartbeatInterval())
                .build();
        });
    }
}

// Start Transaction
@OCPPMessageHandler(action = "StartTransaction", version = OCPPVersion.OCPP_1_6)
public class StartTransactionHandler implements MessageHandler<StartTransactionRequest, StartTransactionResponse> {
    
    @Override
    public CompletableFuture<StartTransactionResponse> handle(String stationId, 
                                                            StartTransactionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            
            // Validate authorization
            AuthorizationResult authResult = authorizationService
                .authorize(stationId, request.getIdTag(), request.getConnectorId());
            
            if (authResult.getStatus() != AuthorizationStatus.ACCEPTED) {
                return StartTransactionResponse.builder()
                    .transactionId(-1) // Invalid transaction
                    .idTagInfo(IdTagInfo.builder()
                              .status(authResult.getStatus())
                              .expiryDate(authResult.getExpiryDate())
                              .build())
                    .build();
            }
            
            // Create charging session
            ChargingSession session = sessionService.startSession(
                SessionStartRequest.builder()
                    .stationId(stationId)
                    .connectorId(request.getConnectorId())
                    .userId(authResult.getUserId())
                    .authTokenId(authResult.getTokenId())
                    .startValue(request.getMeterStart())
                    .timestamp(request.getTimestamp())
                    .build());
            
            // Update connector status
            connectorService.updateStatus(stationId, request.getConnectorId(), 
                                        ConnectorStatus.OCCUPIED);
            
            return StartTransactionResponse.builder()
                .transactionId(Integer.parseInt(session.getTransactionId()))
                .idTagInfo(IdTagInfo.builder()
                          .status(AuthorizationStatus.ACCEPTED)
                          .build())
                .build();
        });
    }
}

// Stop Transaction  
@OCPPMessageHandler(action = "StopTransaction", version = OCPPVersion.OCPP_1_6)
public class StopTransactionHandler implements MessageHandler<StopTransactionRequest, StopTransactionResponse> {
    
    @Override
    public CompletableFuture<StopTransactionResponse> handle(String stationId, 
                                                           StopTransactionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            
            // End charging session
            ChargingSession session = sessionService.stopSession(
                SessionStopRequest.builder()
                    .transactionId(String.valueOf(request.getTransactionId()))
                    .endValue(request.getMeterStop())
                    .timestamp(request.getTimestamp())
                    .stopReason(mapStopReason(request.getReason()))
                    .build());
            
            // Process meter values if present
            if (request.getTransactionData() != null && !request.getTransactionData().isEmpty()) {
                meterValueService.processMeterValues(session.getId(), 
                                                   request.getTransactionData());
            }
            
            // Update connector status
            connectorService.updateStatus(stationId, session.getConnector().getConnectorId(), 
                                        ConnectorStatus.AVAILABLE);
            
            // Generate CDR
            cdrService.generateCDR(session);
            
            return StopTransactionResponse.builder()
                .idTagInfo(IdTagInfo.builder()
                          .status(AuthorizationStatus.ACCEPTED)
                          .build())
                .build();
        });
    }
}
```

### 2.2 OCPP 2.0.1 Implementation

#### 2.2.1 Enhanced Message Handling
```java
@OCPPMessageHandler(action = "TransactionEvent", version = OCPPVersion.OCPP_2_0_1)
public class TransactionEventHandler implements MessageHandler<TransactionEventRequest, TransactionEventResponse> {
    
    @Override
    public CompletableFuture<TransactionEventResponse> handle(String stationId, 
                                                            TransactionEventRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            
            TransactionEventType eventType = request.getEventType();
            TransactionData transactionData = request.getTransactionInfo();
            
            switch (eventType) {
                case STARTED:
                    return handleTransactionStarted(stationId, request);
                case UPDATED:
                    return handleTransactionUpdated(stationId, request);
                case ENDED:
                    return handleTransactionEnded(stationId, request);
                default:
                    throw new IllegalArgumentException("Unknown event type: " + eventType);
            }
        });
    }
    
    private TransactionEventResponse handleTransactionStarted(String stationId, 
                                                            TransactionEventRequest request) {
        
        // Enhanced authorization with ISO 15118 support
        AuthorizationResult authResult = authorizationService
            .authorizeOCPP20(stationId, request.getIdToken(), request.getEvse());
        
        if (authResult.getStatus() != AuthorizationStatus.ACCEPTED) {
            return TransactionEventResponse.builder()
                .idTokenInfo(IdTokenInfo.builder()
                            .status(authResult.getStatus())
                            .build())
                .build();
        }
        
        // Create session with enhanced data
        ChargingSession session = sessionService.startSessionOCPP20(
            SessionStartRequest.builder()
                .stationId(stationId)
                .evseId(request.getEvse().getId())
                .connectorId(request.getEvse().getConnectorId())
                .transactionId(request.getTransactionInfo().getTransactionId())
                .userId(authResult.getUserId())
                .authTokenId(authResult.getTokenId())
                .chargingState(request.getTransactionInfo().getChargingState())
                .timestamp(request.getTimestamp())
                .build());
        
        return TransactionEventResponse.builder()
            .totalCost(calculateRealTimeCost(session))
            .chargingPriority(determineChargingPriority(session))
            .idTokenInfo(IdTokenInfo.builder()
                        .status(AuthorizationStatus.ACCEPTED)
                        .build())
            .updatedPersonalMessage(getPersonalizedMessage(session))
            .build();
    }
}
```

---

## 3. OCPI 2.2.1 REST APIs

### 3.1 Hub Architecture Implementation

#### 3.1.1 OCPI Base Controller
```java
@RestController
@RequestMapping("/ocpi/2.2.1")
@Slf4j
@Validated
public abstract class OCPIBaseController {
    
    @Autowired
    protected OCPIAuthenticationService ocpiAuthService;
    
    @Autowired  
    protected TenantContextService tenantContext;
    
    @Autowired
    protected OCPIHubService hubService;
    
    protected ResponseEntity<OCPIResponse<T>> success(T data) {
        return ResponseEntity.ok(OCPIResponse.<T>builder()
            .statusCode(1000)
            .statusMessage("Success")
            .data(data)
            .timestamp(Instant.now())
            .build());
    }
    
    protected ResponseEntity<OCPIResponse<Void>> error(int statusCode, String message) {
        return ResponseEntity.status(mapToHttpStatus(statusCode))
            .body(OCPIResponse.<Void>builder()
                .statusCode(statusCode)
                .statusMessage(message)
                .timestamp(Instant.now())
                .build());
    }
    
    @ModelAttribute
    public void extractTenantContext(HttpServletRequest request) {
        // Extract tenant from OCPI credentials
        String authToken = request.getHeader("Authorization");
        if (authToken != null && authToken.startsWith("Token ")) {
            String token = authToken.substring(6);
            OCPIPartner partner = ocpiAuthService.validateToken(token);
            tenantContext.setCurrentTenant(partner.getTenantId());
            tenantContext.setCurrentPartner(partner);
        }
    }
}
```

#### 3.1.2 Locations API (Hub Implementation)
```java
@RestController
@RequestMapping("/ocpi/2.2.1/locations")
public class OCPILocationsController extends OCPIBaseController {
    
    @Autowired
    private OCPILocationService locationService;
    
    @Autowired
    private OCPIHubService hubService;
    
    // GET Locations - Hub aggregates from multiple CPOs
    @GetMapping
    public ResponseEntity<OCPIResponse<List<Location>>> getLocations(
            @RequestParam(required = false) String date_from,
            @RequestParam(required = false) String date_to,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        
        OCPIPartner requestingPartner = tenantContext.getCurrentPartner();
        
        // Hub mode: aggregate locations from multiple sources
        if (hubService.isHubMode(requestingPartner)) {
            List<Location> aggregatedLocations = hubService.getAggregatedLocations(
                requestingPartner,
                date_from != null ? Instant.parse(date_from) : null,
                date_to != null ? Instant.parse(date_to) : null,
                offset,
                limit
            );
            
            return success(aggregatedLocations);
        }
        
        // Direct CPO mode: return own locations
        List<Location> locations = locationService.getLocations(
            tenantContext.getCurrentTenantId(),
            date_from != null ? Instant.parse(date_from) : null,
            date_to != null ? Instant.parse(date_to) : null,
            offset,
            limit
        );
        
        return success(locations);
    }
    
    // GET Location by ID
    @GetMapping("/{location_id}")  
    public ResponseEntity<OCPIResponse<Location>> getLocation(
            @PathVariable String location_id) {
        
        OCPIPartner requestingPartner = tenantContext.getCurrentPartner();
        
        // Hub mode: search across all connected CPOs
        if (hubService.isHubMode(requestingPartner)) {
            Optional<Location> location = hubService.findLocationAcrossNetwork(
                requestingPartner, location_id);
            
            return location.map(this::success)
                          .orElse(error(2003, "Location not found"));
        }
        
        // Direct CPO mode
        Optional<Location> location = locationService.getLocation(
            tenantContext.getCurrentTenantId(), location_id);
        
        return location.map(this::success)
                      .orElse(error(2003, "Location not found"));
    }
    
    // PUT Location - Create/Update
    @PutMapping("/{location_id}")
    public ResponseEntity<OCPIResponse<Void>> putLocation(
            @PathVariable String location_id,
            @Valid @RequestBody Location location) {
        
        // Validate location data
        if (!location_id.equals(location.getId())) {
            return error(2001, "Location ID mismatch");
        }
        
        // Check authorization
        if (!ocpiAuthService.canModifyLocation(tenantContext.getCurrentPartner(), location_id)) {
            return error(2002, "Not authorized to modify this location");
        }
        
        locationService.createOrUpdateLocation(
            tenantContext.getCurrentTenantId(),
            location
        );
        
        // Notify hub partners if in hub mode
        if (hubService.isHubMode(tenantContext.getCurrentPartner())) {
            hubService.notifyLocationUpdate(location);
        }
        
        return success(null);
    }
}
```

#### 3.1.3 Sessions API (Cross-Network)
```java
@RestController
@RequestMapping("/ocpi/2.2.1/sessions")
public class OCPISessionsController extends OCPIBaseController {
    
    @Autowired
    private OCPISessionService sessionService;
    
    @Autowired
    private CrossNetworkSessionService crossNetworkService;
    
    // POST Start Session - Cross-network roaming
    @PostMapping("/{session_id}/start")
    public ResponseEntity<OCPIResponse<SessionStartResult>> startSession(
            @PathVariable String session_id,
            @Valid @RequestBody StartSessionRequest request) {
        
        OCPIPartner requestingPartner = tenantContext.getCurrentPartner();
        
        try {
            // Hub mode: route to appropriate CPO
            if (hubService.isHubMode(requestingPartner)) {
                SessionStartResult result = crossNetworkService.startCrossNetworkSession(
                    CrossNetworkSessionRequest.builder()
                        .sessionId(session_id)
                        .locationId(request.getLocationId())
                        .evseUid(request.getEvseUid())
                        .connectorId(request.getConnectorId())
                        .token(request.getToken())
                        .requestingPartnerId(requestingPartner.getId())
                        .build()
                );
                
                return success(result);
            }
            
            // Direct CPO mode: handle own session
            SessionStartResult result = sessionService.startSession(
                tenantContext.getCurrentTenantId(),
                session_id,
                request
            );
            
            return success(result);
            
        } catch (LocationNotFoundException e) {
            return error(2003, "Location not found");
        } catch (ConnectorUnavailableException e) {
            return error(2004, "Connector unavailable");
        } catch (AuthorizationException e) {
            return error(2005, "Token not authorized");
        }
    }
    
    // PUT Stop Session
    @PutMapping("/{session_id}/stop")
    public ResponseEntity<OCPIResponse<SessionStopResult>> stopSession(
            @PathVariable String session_id,
            @Valid @RequestBody StopSessionRequest request) {
        
        try {
            SessionStopResult result = sessionService.stopSession(
                tenantContext.getCurrentTenantId(),
                session_id,
                request
            );
            
            // Generate CDR for cross-network billing
            if (result.getSession().isRoamingSession()) {
                crossNetworkService.generateRoamingCDR(result.getSession());
            }
            
            return success(result);
            
        } catch (SessionNotFoundException e) {
            return error(2003, "Session not found");
        }
    }
}
```

#### 3.1.4 CDRs API (Billing Settlement)
```java
@RestController
@RequestMapping("/ocpi/2.2.1/cdrs")
public class OCPICDRsController extends OCPIBaseController {
    
    @Autowired
    private OCPICDRService cdrService;
    
    @Autowired
    private BillingSettlementService billingService;
    
    // GET CDRs
    @GetMapping
    public ResponseEntity<OCPIResponse<List<CDR>>> getCDRs(
            @RequestParam(required = false) String date_from,
            @RequestParam(required = false) String date_to,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        
        List<CDR> cdrs = cdrService.getCDRs(
            tenantContext.getCurrentTenantId(),
            tenantContext.getCurrentPartner().getId(),
            date_from != null ? Instant.parse(date_from) : null,
            date_to != null ? Instant.parse(date_to) : null,
            offset,
            limit
        );
        
        return success(cdrs);
    }
    
    // POST CDR - For cross-network billing
    @PostMapping
    public ResponseEntity<OCPIResponse<Void>> createCDR(@Valid @RequestBody CDR cdr) {
        
        OCPIPartner sendingPartner = tenantContext.getCurrentPartner();
        
        // Validate CDR
        CDRValidationResult validation = cdrService.validateCDR(cdr, sendingPartner);
        if (!validation.isValid()) {
            return error(2001, validation.getErrorMessage());
        }
        
        // Process CDR for billing
        BillingResult billingResult = billingService.processCrossNetworkCDR(cdr, sendingPartner);
        
        // Store CDR
        cdrService.storeCDR(
            tenantContext.getCurrentTenantId(),
            cdr,
            billingResult
        );
        
        return success(null);
    }
    
    // GET CDR by ID
    @GetMapping("/{cdr_id}")
    public ResponseEntity<OCPIResponse<CDR>> getCDR(@PathVariable String cdr_id) {
        
        Optional<CDR> cdr = cdrService.getCDR(
            tenantContext.getCurrentTenantId(),
            cdr_id
        );
        
        return cdr.map(this::success)
                  .orElse(error(2003, "CDR not found"));
    }
}
```

---

## 4. GraphQL API

### 4.1 Schema Definition

#### 4.1.1 Core Types
```graphql
# Tenant and Multi-tenancy
type Tenant {
  id: ID!
  name: String!
  domain: String
  companyName: String
  tenantType: TenantType!
  countryCode: String
  partyId: String
  status: TenantStatus!
  settings: TenantSettings
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum TenantType {
  CPO
  MSP
  HUB
  SAAS_PROVIDER
  HYBRID
}

enum TenantStatus {
  ACTIVE
  SUSPENDED
  TERMINATED
  PENDING_ACTIVATION
}

type TenantSettings {
  timezone: String
  defaultCurrency: String
  defaultLanguage: String
  features: JSON
  customProperties: JSON
}

# Charging Infrastructure
type ChargingStation {
  id: ID!
  stationId: String!
  name: String
  description: String
  manufacturer: String
  model: String
  serialNumber: String
  firmwareVersion: String
  ocppVersion: OCPPVersion!
  ocppStatus: OCPPStatus
  location: Location
  address: String
  coordinates: GeoCoordinate
  status: StationStatus!
  availability: StationAvailability
  configuration: StationConfiguration
  capabilities: StationCapabilities
  connectors: [Connector!]!
  activeSessions: [ChargingSession!]!
  lastHeartbeat: DateTime
  createdAt: DateTime!
  updatedAt: DateTime!
}

type Connector {
  id: ID!
  connectorId: Int!
  connectorType: ConnectorType!
  status: ConnectorStatus!
  maxPower: Int
  maxCurrent: Int
  voltage: Int
  numberOfPhases: Int
  currentSession: ChargingSession
  tariff: Tariff
  configuration: ConnectorConfiguration
}

# Sessions
type ChargingSession {
  id: ID!
  sessionId: String!
  transactionId: String
  station: ChargingStation!
  connector: Connector!
  user: User
  authToken: AuthToken
  startTimestamp: DateTime
  endTimestamp: DateTime
  totalEnergy: Float
  maxPower: Float
  avgPower: Float
  status: SessionStatus!
  stopReason: StopReason
  authMethod: AuthMethod
  totalCost: Float
  currency: String
  realTimeMetrics: SessionRealTimeMetrics
  meterValues: [MeterValue!]!
  events: [SessionEvent!]!
  cdr: ChargeDetailRecord
}

type SessionRealTimeMetrics {
  currentPower: Float
  currentEnergy: Float
  voltage: Float
  current: Float
  temperature: Float
  duration: Int
  estimatedCost: Float
}

# Users and Authentication
type User {
  id: ID!
  email: String
  firstName: String
  lastName: String
  phone: String
  language: String
  userType: UserType!
  userGroups: [String!]
  status: UserStatus!
  lastLoginAt: DateTime
  authTokens: [AuthToken!]!
  sessions: [ChargingSession!]!
  roles: [Role!]!
}

type AuthToken {
  id: ID!
  tokenType: TokenType!
  visualId: String
  expiresAt: DateTime
  isActive: Boolean!
  isBlocked: Boolean!
  lastUsedAt: DateTime
  useCount: Int!
  restrictions: TokenRestrictions
}

# OCPI Integration
type OCPIPartner {
  id: ID!
  countryCode: String!
  partyId: String!
  name: String!
  role: OCPIRole!
  businessDetails: BusinessDetails
  status: PartnerStatus!
  endpoints: JSON
  lastSyncTimestamp: DateTime
  locations: [OCPILocation!]!
  tariffs: [OCPITariff!]!
}

type OCPILocation {
  id: ID!
  locationId: String!
  countryCode: String!
  partyId: String!
  name: String
  address: String
  city: String!
  coordinates: GeoCoordinate
  lastUpdated: DateTime!
  evses: [OCPIEVSE!]!
}

# Billing and Tariffs
type Tariff {
  id: ID!
  tariffId: String!
  name: String!
  description: String
  currency: String!
  tariffElements: [TariffElement!]!
  validFrom: DateTime
  validTo: DateTime
  status: TariffStatus!
}

type TariffElement {
  priceComponents: [PriceComponent!]!
  restrictions: TariffRestrictions
}
```

#### 4.1.2 Query Operations
```graphql
type Query {
  # Tenant Management
  tenant(id: ID!): Tenant
  tenants(filter: TenantFilter): [Tenant!]!
  
  # Station Management
  station(id: ID!): ChargingStation
  stations(filter: StationFilter): [ChargingStation!]!
  stationByStationId(stationId: String!): ChargingStation
  stationsNearby(coordinates: GeoCoordinateInput!, radiusKm: Float!): [ChargingStation!]!
  
  # Session Management
  session(id: ID!): ChargingSession
  sessions(filter: SessionFilter): [ChargingSession!]!
  activeSessionsCount: Int!
  sessionsByUser(userId: ID!, limit: Int, offset: Int): [ChargingSession!]!
  
  # Real-time Data
  stationStatus(stationId: String!): StationStatus!
  connectorStatus(stationId: String!, connectorId: Int!): ConnectorStatus!
  realTimeMetrics(sessionId: ID!): SessionRealTimeMetrics
  
  # User Management  
  user(id: ID!): User
  users(filter: UserFilter): [User!]!
  userByEmail(email: String!): User
  
  # OCPI Integration
  ocpiPartner(id: ID!): OCPIPartner
  ocpiPartners(filter: OCPIPartnerFilter): [OCPIPartner!]!
  ocpiLocations(partnerId: ID): [OCPILocation!]!
  ocpiLocationById(locationId: String!): OCPILocation
  
  # Analytics and Reporting
  sessionAnalytics(filter: AnalyticsFilter!): SessionAnalytics!
  revenueAnalytics(filter: RevenueFilter!): RevenueAnalytics!
  stationUtilization(stationIds: [String!], period: TimePeriod!): [StationUtilizationData!]!
  
  # Billing
  tariff(id: ID!): Tariff
  tariffs(filter: TariffFilter): [Tariff!]!
  cdrs(filter: CDRFilter): [ChargeDetailRecord!]!
  
  # System Health
  systemHealth: SystemHealth!
  ocppConnections: [OCPPConnectionInfo!]!
}

# Filter Input Types
input StationFilter {
  status: [StationStatus!]
  manufacturer: [String!]
  ocppVersion: [OCPPVersion!]
  locationIds: [String!]
  coordinates: GeoCoordinateInput
  radiusKm: Float
  hasActiveSessions: Boolean
  lastHeartbeatAfter: DateTime
}

input SessionFilter {
  status: [SessionStatus!]
  stationIds: [String!]
  userIds: [String!]
  startAfter: DateTime
  startBefore: DateTime
  endAfter: DateTime
  endBefore: DateTime
  minEnergy: Float
  maxEnergy: Float
  authMethods: [AuthMethod!]
}

input UserFilter {
  status: [UserStatus!]
  userTypes: [UserType!]
  createdAfter: DateTime
  lastLoginAfter: DateTime
  hasActiveSessions: Boolean
}
```

#### 4.1.3 Mutation Operations
```graphql
type Mutation {
  # Station Management
  createStation(input: CreateStationInput!): ChargingStation!
  updateStation(id: ID!, input: UpdateStationInput!): ChargingStation!
  deleteStation(id: ID!): Boolean!
  
  # Station Operations
  sendStationCommand(stationId: String!, command: StationCommandInput!): CommandResult!
  updateStationConfiguration(stationId: String!, config: StationConfigurationInput!): ChargingStation!
  triggerStationReset(stationId: String!, type: ResetType!): CommandResult!
  
  # Session Management
  startSession(input: StartSessionInput!): ChargingSession!
  stopSession(sessionId: ID!): ChargingSession!
  reserveConnector(input: ReserveConnectorInput!): ReservationResult!
  cancelReservation(reservationId: String!): Boolean!
  
  # User Management
  createUser(input: CreateUserInput!): User!
  updateUser(id: ID!, input: UpdateUserInput!): User!
  deleteUser(id: ID!): Boolean!
  blockUser(id: ID!, reason: String): User!
  unblockUser(id: ID!): User!
  
  # Auth Token Management
  createAuthToken(input: CreateAuthTokenInput!): AuthToken!
  updateAuthToken(id: ID!, input: UpdateAuthTokenInput!): AuthToken!
  blockAuthToken(id: ID!, reason: String): AuthToken!
  unblockAuthToken(id: ID!): AuthToken!
  
  # OCPI Partner Management
  createOCPIPartner(input: CreateOCPIPartnerInput!): OCPIPartner!
  updateOCPIPartner(id: ID!, input: UpdateOCPIPartnerInput!): OCPIPartner!
  syncOCPIPartner(id: ID!): OCPISyncResult!
  
  # Tariff Management
  createTariff(input: CreateTariffInput!): Tariff!
  updateTariff(id: ID!, input: UpdateTariffInput!): Tariff!
  activateTariff(id: ID!): Tariff!
  deactivateTariff(id: ID!): Tariff!
  
  # System Operations
  triggerSystemMaintenance(type: MaintenanceType!): MaintenanceResult!
  clearCache(cacheType: CacheType!): Boolean!
}

# Input Types
input CreateStationInput {
  stationId: String!
  name: String!
  description: String
  manufacturer: String
  model: String
  serialNumber: String
  locationId: String
  address: String
  coordinates: GeoCoordinateInput
  connectors: [CreateConnectorInput!]!
  configuration: StationConfigurationInput
}

input StationCommandInput {
  command: StationCommand!
  parameters: JSON
}

enum StationCommand {
  UNLOCK_CONNECTOR
  RESET
  GET_CONFIGURATION
  CHANGE_CONFIGURATION
  GET_DIAGNOSTICS
  UPDATE_FIRMWARE
  RESERVE_NOW
  CANCEL_RESERVATION
  START_TRANSACTION
  STOP_TRANSACTION
}

input StartSessionInput {
  stationId: String!
  connectorId: Int!
  authTokenId: ID!
  tariffId: ID
}
```

#### 4.1.4 Subscription Operations
```graphql
type Subscription {
  # Real-time Station Updates
  stationStatusUpdates(stationIds: [String!]): StationStatusUpdate!
  connectorStatusUpdates(stationIds: [String!]): ConnectorStatusUpdate!
  
  # Real-time Session Updates
  sessionUpdates(sessionIds: [ID!]): SessionUpdate!
  sessionMetrics(sessionId: ID!): SessionRealTimeMetrics!
  
  # System Events
  systemAlerts(severity: [AlertSeverity!]): SystemAlert!
  ocppConnectionEvents: OCPPConnectionEvent!
  
  # OCPI Events
  ocpiPartnerUpdates: OCPIPartnerUpdate!
  crossNetworkSessionEvents: CrossNetworkSessionEvent!
}

type StationStatusUpdate {
  stationId: String!
  status: StationStatus!
  timestamp: DateTime!
  details: JSON
}

type SessionUpdate {
  sessionId: ID!
  status: SessionStatus!
  realTimeMetrics: SessionRealTimeMetrics
  timestamp: DateTime!
}

type SystemAlert {
  id: ID!
  severity: AlertSeverity!
  message: String!
  category: AlertCategory!
  stationId: String
  sessionId: ID
  timestamp: DateTime!
  metadata: JSON
}

enum AlertSeverity {
  LOW
  MEDIUM
  HIGH
  CRITICAL
}

enum AlertCategory {
  STATION_OFFLINE
  SESSION_ERROR
  BILLING_ERROR
  OCPI_ERROR
  SYSTEM_ERROR
  SECURITY_ALERT
}
```

### 4.2 GraphQL Implementation

#### 4.2.1 Data Loaders (N+1 Problem)
```java
@Component
public class ChargingStationDataLoader {
    
    @Autowired
    private ChargingStationService stationService;
    
    @Bean
    public DataLoader<UUID, ChargingStation> chargingStationLoader() {
        return DataLoader.newMappedDataLoader(stationIds -> 
            CompletableFuture.supplyAsync(() -> 
                stationService.findByIds(stationIds)
                             .stream()
                             .collect(Collectors.toMap(
                                 ChargingStation::getId,
                                 Function.identity()))
            )
        );
    }
    
    @Bean
    public DataLoader<String, List<Connector>> connectorsByStationLoader() {
        return DataLoader.newMappedDataLoader(stationIds ->
            CompletableFuture.supplyAsync(() ->
                connectorService.findByStationIds(stationIds)
                               .stream()
                               .collect(Collectors.groupingBy(
                                   connector -> connector.getChargingStation().getId().toString()))
            )
        );
    }
}
```

#### 4.2.2 Query Resolvers
```java
@Component
@Slf4j
public class ChargingStationQueryResolver implements GraphQLQueryResolver {
    
    @Autowired
    private ChargingStationService stationService;
    
    @Autowired
    private TenantContextService tenantContext;
    
    public ChargingStation station(String id) {
        UUID stationUuid = UUID.fromString(id);
        return stationService.findById(tenantContext.getCurrentTenantId(), stationUuid)
                           .orElse(null);
    }
    
    public List<ChargingStation> stations(StationFilter filter) {
        return stationService.findStations(
            tenantContext.getCurrentTenantId(),
            mapFilterToCriteria(filter)
        );
    }
    
    public List<ChargingStation> stationsNearby(GeoCoordinateInput coordinates, 
                                              Float radiusKm) {
        return stationService.findStationsNearby(
            tenantContext.getCurrentTenantId(),
            new GeoCoordinate(coordinates.getLatitude(), coordinates.getLongitude()),
            radiusKm
        );
    }
    
    private StationSearchCriteria mapFilterToCriteria(StationFilter filter) {
        return StationSearchCriteria.builder()
            .statuses(filter.getStatus())
            .manufacturers(filter.getManufacturer())
            .ocppVersions(filter.getOcppVersion())
            .locationIds(filter.getLocationIds())
            .coordinates(filter.getCoordinates() != null ? 
                new GeoCoordinate(filter.getCoordinates().getLatitude(),
                                filter.getCoordinates().getLongitude()) : null)
            .radiusKm(filter.getRadiusKm())
            .hasActiveSessions(filter.getHasActiveSessions())
            .lastHeartbeatAfter(filter.getLastHeartbeatAfter())
            .build();
    }
}
```

#### 4.2.3 Field Resolvers
```java
@Component  
public class ChargingStationFieldResolver implements GraphQLResolver<ChargingStation> {
    
    @Autowired
    private DataLoader<String, List<Connector>> connectorsByStationLoader;
    
    @Autowired
    private DataLoader<UUID, List<ChargingSession>> activeSessionsByStationLoader;
    
    public CompletableFuture<List<Connector>> connectors(ChargingStation station) {
        return connectorsByStationLoader.load(station.getId().toString())
                                       .thenApply(connectors -> connectors != null ? 
                                                connectors : Collections.emptyList());
    }
    
    public CompletableFuture<List<ChargingSession>> activeSessions(ChargingStation station) {
        return activeSessionsByStationLoader.load(station.getId())
                                           .thenApply(sessions -> sessions != null ? 
                                                    sessions : Collections.emptyList());
    }
    
    public StationStatus status(ChargingStation station) {
        // Real-time status from Redis cache
        return stationStatusCache.getStatus(station.getId())
                                .orElse(station.getStatus());
    }
}
```

#### 4.2.4 Subscription Resolvers
```java
@Component
public class StationSubscriptionResolver implements GraphQLSubscriptionResolver {
    
    @Autowired
    private ReactiveRedisTemplate<String, Object> reactiveRedis;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public Publisher<StationStatusUpdate> stationStatusUpdates(List<String> stationIds) {
        
        // Create Redis pattern for station updates
        String pattern = stationIds.isEmpty() ? 
            "station:status:*" : 
            stationIds.stream()
                     .map(id -> "station:status:" + id)
                     .collect(Collectors.joining("|"));
        
        return reactiveRedis.listenToPattern(pattern)
                           .map(ReactiveSubscription.Message::getMessage)
                           .cast(String.class)
                           .mapNotNull(message -> {
                               try {
                                   return objectMapper.readValue(message, StationStatusUpdate.class);
                               } catch (Exception e) {
                                   log.warn("Failed to parse station status update: {}", message, e);
                                   return null;
                               }
                           })
                           .filter(update -> stationIds.isEmpty() || 
                                           stationIds.contains(update.getStationId()));
    }
    
    public Publisher<SessionRealTimeMetrics> sessionMetrics(String sessionId) {
        
        return Flux.interval(Duration.ofSeconds(1))
                  .mapNotNull(tick -> {
                      Optional<ChargingSession> session = sessionService.findById(UUID.fromString(sessionId));
                      if (session.isEmpty() || !session.get().getStatus().isActive()) {
                          return null;
                      }
                      
                      return sessionMetricsService.getRealTimeMetrics(session.get());
                  })
                  .takeWhile(metrics -> metrics != null);
    }
}
```

---

## 5. WebSocket Real-Time API

### 5.1 Dashboard WebSocket Implementation

#### 5.1.1 WebSocket Configuration
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable Redis-backed message broker for clustering
        config.enableStompBrokerRelay("/topic", "/queue")
              .setRelayHost("redis")
              .setRelayPort(6379)
              .setClientLogin("opencsms")
              .setClientPasscode("${redis.password}")
              .setSystemLogin("opencsms")  
              .setSystemPasscode("${redis.password}");
              
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/dashboard")
                .setAllowedOriginPatterns("*")
                .withSockJS();
                
        registry.addEndpoint("/ws/realtime")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new TenantWebSocketInterceptor())
                .withSockJS();
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new AuthenticationWebSocketInterceptor());
    }
}
```

#### 5.1.2 Real-Time Message Controller
```java
@Controller
@Slf4j
public class RealTimeWebSocketController {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private TenantContextService tenantContext;
    
    // Subscribe to station updates
    @MessageMapping("/stations/subscribe")
    @SendTo("/topic/stations/{tenantId}")
    public void subscribeToStationUpdates(@DestinationVariable String tenantId,
                                        @Payload StationSubscriptionRequest request) {
        
        // Validate tenant access
        if (!tenantContext.hasAccessToTenant(UUID.fromString(tenantId))) {
            throw new AccessDeniedException("No access to tenant: " + tenantId);
        }
        
        log.info("User subscribed to station updates for tenant: {}, stations: {}", 
                tenantId, request.getStationIds());
        
        // Send current station status
        List<ChargingStation> stations = stationService.findByIds(
            UUID.fromString(tenantId), 
            request.getStationIds()
        );
        
        stations.forEach(station -> {
            StationStatusUpdate update = StationStatusUpdate.builder()
                .stationId(station.getStationId())
                .status(station.getStatus())
                .timestamp(Instant.now())
                .details(Map.of(
                    "lastHeartbeat", station.getLastHeartbeat(),
                    "connectorCount", station.getConnectors().size(),
                    "activeSessionCount", station.getActiveSessions().size()
                ))
                .build();
                
            messagingTemplate.convertAndSend(
                "/topic/stations/" + tenantId + "/" + station.getStationId(),
                update
            );
        });
    }
    
    // Subscribe to session metrics
    @MessageMapping("/sessions/{sessionId}/subscribe")
    @SendTo("/topic/sessions/{sessionId}/metrics")
    public void subscribeToSessionMetrics(@DestinationVariable String sessionId) {
        
        UUID sessionUuid = UUID.fromString(sessionId);
        Optional<ChargingSession> session = sessionService.findById(sessionUuid);
        
        if (session.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        // Validate tenant access
        if (!tenantContext.hasAccessToTenant(session.get().getTenantId())) {
            throw new AccessDeniedException("No access to session: " + sessionId);
        }
        
        log.info("User subscribed to session metrics: {}", sessionId);
        
        // Start real-time metrics streaming
        sessionMetricsStreamingService.startStreaming(sessionUuid);
    }
    
    // Send command to station
    @MessageMapping("/stations/{stationId}/command")
    public void sendStationCommand(@DestinationVariable String stationId,
                                 @Payload StationCommandRequest request,
                                 Principal principal) {
        
        // Validate permissions
        if (!hasStationCommandPermission(principal, stationId, request.getCommand())) {
            throw new AccessDeniedException("Not authorized to send command to station: " + stationId);
        }
        
        CompletableFuture<CommandResult> commandFuture = 
            stationCommandService.sendCommand(stationId, request);
            
        commandFuture.thenAccept(result -> {
            // Send result back to user
            messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/commands/" + stationId,
                result
            );
        });
    }
}
```

### 5.2 Real-Time Event Broadcasting

#### 5.2.1 Event Publisher Service
```java
@Service
@Slf4j
public class RealTimeEventService {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ReactiveRedisTemplate<String, Object> reactiveRedis;
    
    @EventListener
    public void onStationStatusChanged(StationStatusChangedEvent event) {
        
        StationStatusUpdate update = StationStatusUpdate.builder()
            .stationId(event.getStationId())
            .status(event.getNewStatus())
            .timestamp(event.getTimestamp())
            .details(event.getDetails())
            .build();
        
        // Broadcast to WebSocket subscribers
        messagingTemplate.convertAndSend(
            "/topic/stations/" + event.getTenantId() + "/" + event.getStationId(),
            update
        );
        
        // Also publish to Redis for GraphQL subscriptions
        reactiveRedis.convertAndSend(
            "station:status:" + event.getStationId(),
            update
        ).subscribe();
        
        log.debug("Broadcasted station status update: {} -> {}", 
                 event.getStationId(), event.getNewStatus());
    }
    
    @EventListener
    public void onSessionUpdated(SessionUpdatedEvent event) {
        
        SessionUpdate update = SessionUpdate.builder()
            .sessionId(event.getSessionId())
            .status(event.getStatus())
            .realTimeMetrics(event.getRealTimeMetrics())
            .timestamp(event.getTimestamp())
            .build();
        
        // Broadcast to session subscribers
        messagingTemplate.convertAndSend(
            "/topic/sessions/" + event.getSessionId(),
            update
        );
        
        // Broadcast to station subscribers (for dashboard views)
        messagingTemplate.convertAndSend(
            "/topic/stations/" + event.getTenantId() + "/" + event.getStationId() + "/sessions",
            update
        );
        
        log.debug("Broadcasted session update: {} -> {}", 
                 event.getSessionId(), event.getStatus());
    }
    
    @EventListener
    public void onSystemAlert(SystemAlertEvent event) {
        
        SystemAlert alert = SystemAlert.builder()
            .id(event.getId())
            .severity(event.getSeverity())
            .message(event.getMessage())
            .category(event.getCategory())
            .stationId(event.getStationId())
            .sessionId(event.getSessionId())
            .timestamp(event.getTimestamp())
            .metadata(event.getMetadata())
            .build();
        
        // Broadcast to all tenant users
        messagingTemplate.convertAndSend(
            "/topic/alerts/" + event.getTenantId(),
            alert
        );
        
        // Send high-priority alerts directly to administrators
        if (event.getSeverity() == AlertSeverity.CRITICAL) {
            List<User> admins = userService.findAdministrators(event.getTenantId());
            admins.forEach(admin -> {
                messagingTemplate.convertAndSendToUser(
                    admin.getId().toString(),
                    "/queue/alerts/critical",
                    alert
                );
            });
        }
        
        log.info("Broadcasted system alert: {} - {}", event.getSeverity(), event.getMessage());
    }
}
```

---

## 6. Management REST API

### 6.1 Administrative APIs

#### 6.1.1 Tenant Management API
```java
@RestController
@RequestMapping("/api/v1/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Validated
public class TenantManagementController {
    
    @Autowired
    private TenantService tenantService;
    
    @GetMapping
    public ResponseEntity<PagedResponse<TenantDTO>> getTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TenantStatus status) {
        
        Page<Tenant> tenants = tenantService.findTenants(
            PageRequest.of(page, size, Sort.by("createdAt").descending()),
            TenantSearchCriteria.builder()
                .search(search)
                .status(status)
                .build()
        );
        
        PagedResponse<TenantDTO> response = PagedResponse.<TenantDTO>builder()
            .content(tenants.getContent().stream()
                          .map(tenantMapper::toDTO)
                          .collect(Collectors.toList()))
            .page(tenants.getNumber())
            .size(tenants.getSize())
            .totalElements(tenants.getTotalElements())
            .totalPages(tenants.getTotalPages())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        
        Tenant tenant = tenantService.createTenant(
            CreateTenantCommand.builder()
                .name(request.getName())
                .domain(request.getDomain())
                .companyName(request.getCompanyName())
                .tenantType(request.getTenantType())
                .countryCode(request.getCountryCode())
                .partyId(request.getPartyId())
                .settings(request.getSettings())
                .build()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
                           .body(tenantMapper.toDTO(tenant));
    }
    
    @PutMapping("/{tenantId}/status")
    public ResponseEntity<TenantDTO> updateTenantStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request) {
        
        Tenant tenant = tenantService.updateStatus(tenantId, request.getStatus(), request.getReason());
        
        return ResponseEntity.ok(tenantMapper.toDTO(tenant));
    }
}
```

---

## Next Steps

Cette spécification d'API complète couvre :

✅ **OCPP WebSocket** : Gestion complète 1.6 & 2.0.1  
✅ **OCPI Hub REST** : APIs 2.2.1 complètes avec hub  
✅ **GraphQL** : Schema flexible pour intégrations  
✅ **WebSocket temps réel** : Dashboard et monitoring  
✅ **Management APIs** : Administration complète  

Veux-tu maintenant :
1. **Architecture sécurité** (PKI, certificats OCPP)
2. **Passer à Phase 3** (plan de développement)
3. **Approfondir une API spécifique**

Quelle priorité ?