# Open-CSMS - Data Models Specification

**Version:** 1.0  
**Date:** 2025-01-07  
**Status:** Draft

---

## 1. Overview

Cette spécification définit les modèles de données complets pour open-csms, incluant :
- **Entités JPA** avec annotations complètes
- **Relations** et contraintes d'intégrité  
- **Multi-tenant** avec isolation performante
- **Audit trail** et versioning
- **Validation** et business rules

### 1.1 Conventions

```java
// Base pour toutes les entités multi-tenant
@MappedSuperclass
public abstract class TenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructeurs, getters, setters
}

// Base pour entities avec audit trail
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends TenantEntity {
    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;
    
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private UUID lastModifiedBy;
    
    @Version
    @Column(name = "version")
    private Long version;
}
```

---

## 2. Core Business Entities

### 2.1 Tenant Management

#### 2.1.1 Tenant Entity
```java
@Entity
@Table(name = "tenants", 
       uniqueConstraints = @UniqueConstraint(columnNames = "domain"))
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tenant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String name;
    
    @Column(name = "domain", unique = true, length = 255)
    @Size(max = 255)
    private String domain;
    
    @Column(name = "company_name", length = 500)
    @Size(max = 500)
    private String companyName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", nullable = false)
    private TenantType tenantType; // CPO, MSP, HUB, SAAS_PROVIDER
    
    @Column(name = "country_code", length = 2)
    @Size(min = 2, max = 2)
    private String countryCode;
    
    @Column(name = "party_id", length = 3)
    @Size(min = 3, max = 3)
    private String partyId; // Pour OCPI
    
    @Type(JsonType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private TenantSettings settings;
    
    @Type(JsonType.class)
    @Column(name = "billing_config", columnDefinition = "jsonb")
    private BillingConfiguration billingConfig;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status; // ACTIVE, SUSPENDED, TERMINATED
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Relations
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ChargingStation> chargingStations = new HashSet<>();
    
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();
}

// Supporting classes
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantSettings {
    private String timezone;
    private String defaultCurrency;
    private String defaultLanguage;
    private Map<String, Object> features;
    private Map<String, Object> customProperties;
    // getters, setters
}

public enum TenantType {
    CPO,              // Charge Point Operator
    MSP,              // Mobility Service Provider
    HUB,              // OCPI Hub
    SAAS_PROVIDER,    // SaaS platform with multiple CPO clients
    HYBRID            // Multiple roles
}

public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    PENDING_ACTIVATION
}
```

### 2.2 User Management

#### 2.2.1 User Entity
```java
@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"tenant_id", "email"}),
           @UniqueConstraint(columnNames = {"tenant_id", "external_id", "auth_provider"})
       },
       indexes = {
           @Index(name = "idx_users_tenant", columnList = "tenant_id"),
           @Index(name = "idx_users_email", columnList = "tenant_id, email"),
           @Index(name = "idx_users_external", columnList = "tenant_id, external_id, auth_provider")
       })
public class User extends AuditableEntity {
    
    @Column(name = "email", length = 255)
    @Email
    @Size(max = 255)
    private String email;
    
    @Column(name = "first_name", length = 255)
    @Size(max = 255)
    private String firstName;
    
    @Column(name = "last_name", length = 255)
    @Size(max = 255)
    private String lastName;
    
    @Column(name = "phone", length = 50)
    @Size(max = 50)
    private String phone;
    
    @Column(name = "language", length = 5)
    @Size(max = 5)
    private String language; // ISO 639-1 + country
    
    // Authentication
    @Column(name = "external_id", length = 255)
    @Size(max = 255)
    private String externalId; // ID from external auth provider
    
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", length = 50)
    private AuthProvider authProvider;
    
    @Type(JsonType.class)
    @Column(name = "auth_attributes", columnDefinition = "jsonb")
    private Map<String, Object> authAttributes;
    
    // User categorization
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType; // INDIVIDUAL, BUSINESS, FLEET, ADMIN
    
    @Type(JsonType.class)
    @Column(name = "user_groups", columnDefinition = "jsonb")
    private Set<String> userGroups;
    
    // Billing and preferences
    @Type(JsonType.class)
    @Column(name = "billing_profile", columnDefinition = "jsonb")
    private BillingProfile billingProfile;
    
    @Type(JsonType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private UserPreferences preferences;
    
    // Status and restrictions
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status; // ACTIVE, SUSPENDED, BLOCKED, PENDING
    
    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    // Relations
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<AuthToken> authTokens = new HashSet<>();
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<ChargingSession> chargingSessions = new HashSet<>();
    
    @ManyToMany
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}

// Supporting enums and classes
public enum AuthProvider {
    LOCAL,           // Local authentication
    OIDC,           // OpenID Connect
    SAML,           // SAML 2.0
    LDAP,           // LDAP/Active Directory
    OAUTH2_GOOGLE,  // Google OAuth2
    OAUTH2_AZURE,   // Azure AD
    OAUTH2_CUSTOM   // Custom OAuth2 provider
}

public enum UserType {
    INDIVIDUAL,     // Individual consumer
    BUSINESS,       // Business user
    FLEET,          // Fleet manager
    ADMIN,          // System administrator
    SERVICE_ACCOUNT // API service account
}

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    BLOCKED,
    PENDING_VERIFICATION,
    DELETED
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingProfile {
    private String defaultPaymentMethod;
    private String billingAddress;
    private String vatNumber;
    private String companyName;
    private Map<String, Object> paymentPreferences;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPreferences {
    private String language;
    private String timezone;
    private String currency;
    private Map<String, Boolean> notifications;
    private Map<String, Object> customSettings;
}
```

#### 2.2.2 Role & Permission Management
```java
@Entity
@Table(name = "roles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
public class Role extends TenantEntity {
    
    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;
    
    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role_type")
    private RoleType roleType; // SYSTEM, TENANT, CUSTOM
    
    @Column(name = "is_default")
    private Boolean isDefault = false;
    
    @ManyToMany
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}

@Entity
@Table(name = "permissions")
public class Permission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "name", nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name; // e.g., "stations.read", "sessions.write"
    
    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;
    
    @Column(name = "resource", nullable = false, length = 50)
    @NotBlank
    private String resource; // stations, sessions, users, etc.
    
    @Column(name = "action", nullable = false, length = 50)
    @NotBlank
    private String action; // create, read, update, delete, execute
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private PermissionScope scope; // GLOBAL, TENANT, OWNED
}

public enum RoleType {
    SYSTEM,    // System-wide roles
    TENANT,    // Tenant-specific roles  
    CUSTOM     // User-defined roles
}

public enum PermissionScope {
    GLOBAL,    // Access across all tenants (super admin)
    TENANT,    // Access within tenant
    OWNED      // Access to owned resources only
}
```

### 2.3 Authentication Tokens

#### 2.3.1 AuthToken Entity
```java
@Entity
@Table(name = "auth_tokens",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "token_value", "token_type"}),
       indexes = {
           @Index(name = "idx_tokens_tenant", columnList = "tenant_id"),
           @Index(name = "idx_tokens_user", columnList = "tenant_id, user_id"),
           @Index(name = "idx_tokens_value", columnList = "tenant_id, token_value, token_type"),
           @Index(name = "idx_tokens_status", columnList = "tenant_id, is_active, expires_at")
       })
public class AuthToken extends AuditableEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType;
    
    @Column(name = "token_value", nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String tokenValue;
    
    @Column(name = "token_hash", length = 128)
    private String tokenHash; // SHA-256 hash for security
    
    @Column(name = "parent_id", length = 50)
    private String parentId; // Pour grouper les tokens (ex: même carte RFID)
    
    @Column(name = "visual_id", length = 50)
    @Size(max = 50)
    private String visualId; // Identifiant visible par l'utilisateur
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_blocked")
    private Boolean isBlocked = false;
    
    @Column(name = "block_reason", length = 255)
    private String blockReason;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "use_count")
    private Long useCount = 0L;
    
    // Métadonnées du token
    @Type(JsonType.class)
    @Column(name = "token_metadata", columnDefinition = "jsonb")
    private TokenMetadata tokenMetadata;
    
    // Restrictions géographiques et temporelles
    @Type(JsonType.class)
    @Column(name = "restrictions", columnDefinition = "jsonb")
    private TokenRestrictions restrictions;
    
    // Relations
    @OneToMany(mappedBy = "authToken", fetch = FetchType.LAZY)
    private Set<ChargingSession> chargingSessions = new HashSet<>();
}

public enum TokenType {
    RFID_ISO14443,      // Standard ISO 14443
    RFID_MIFARE,        // Mifare technology
    NFC_SMARTPHONE,     // NFC émulation smartphone
    QR_CODE_DYNAMIC,    // QR code dynamique app
    ISO15118_CONTRACT,  // Plug & Charge contract
    AUTOCHARGE_VIN,     // Reconnaissance automatique véhicule
    API_KEY,            // Clé API externe
    ROAMING_TOKEN,      // Token roaming autre réseau
    TEMPORARY,          // Token temporaire
    TEST_TOKEN          // Token de test
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenMetadata {
    private String issuer;                    // Émetteur du token
    private String cardType;                  // Type de carte physique
    private String manufacturerData;          // Données constructeur
    private Map<String, String> customFields; // Champs personnalisés
    private String description;               // Description libre
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenRestrictions {
    private Set<String> allowedStations;     // Stations autorisées
    private Set<String> allowedGroups;       // Groupes de stations autorisés
    private TimeRestriction timeRestriction;  // Restrictions horaires
    private GeographicRestriction geographic; // Restrictions géographiques
    private UsageRestriction usage;           // Restrictions d'usage
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRestriction {
    private Set<DayOfWeek> allowedDays;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timezone;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeographicRestriction {
    private Set<String> allowedCountries;
    private Set<String> allowedRegions;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageRestriction {
    private Integer maxSessionsPerDay;
    private Integer maxSessionsPerMonth;
    private BigDecimal maxAmountPerSession;
    private BigDecimal maxAmountPerMonth;
    private Integer maxDurationMinutes;
}
```

---

## 3. Charging Infrastructure

### 3.1 Charging Station

#### 3.1.1 ChargingStation Entity
```java
@Entity
@Table(name = "charging_stations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "station_id"}),
       indexes = {
           @Index(name = "idx_stations_tenant", columnList = "tenant_id"),
           @Index(name = "idx_stations_status", columnList = "tenant_id, status"),
           @Index(name = "idx_stations_location", columnList = "tenant_id, location_id"),
           @Index(name = "idx_stations_ocpp", columnList = "tenant_id, ocpp_version, ocpp_status")
       })
public class ChargingStation extends AuditableEntity {
    
    @Column(name = "station_id", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String stationId; // Identifiant unique OCPP
    
    @Column(name = "name", length = 255)
    @Size(max = 255)
    private String name;
    
    @Column(name = "description", length = 1000)
    @Size(max = 1000)
    private String description;
    
    // Manufacturer information
    @Column(name = "manufacturer", length = 100)
    @Size(max = 100)
    private String manufacturer;
    
    @Column(name = "model", length = 100)
    @Size(max = 100)
    private String model;
    
    @Column(name = "serial_number", length = 100)
    @Size(max = 100)
    private String serialNumber;
    
    @Column(name = "firmware_version", length = 50)
    @Size(max = 50)
    private String firmwareVersion;
    
    // OCPP Configuration
    @Enumerated(EnumType.STRING)
    @Column(name = "ocpp_version", nullable = false)
    private OCPPVersion ocppVersion;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ocpp_status")
    private OCPPStatus ocppStatus;
    
    @Column(name = "ocpp_endpoint", length = 500)
    @Size(max = 500)
    private String ocppEndpoint;
    
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;
    
    @Column(name = "heartbeat_interval")
    private Integer heartbeatInterval; // seconds
    
    // Physical location
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;
    
    @Column(name = "address", length = 500)
    @Size(max = 500)
    private String address;
    
    @Column(name = "latitude")
    @Digits(integer = 3, fraction = 8)
    private BigDecimal latitude;
    
    @Column(name = "longitude")
    @Digits(integer = 3, fraction = 8)
    private BigDecimal longitude;
    
    // Status and availability
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StationStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "availability")
    private StationAvailability availability;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Column(name = "status_info", length = 255)
    private String statusInfo;
    
    // Configuration and capabilities
    @Type(JsonType.class)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private StationConfiguration configuration;
    
    @Type(JsonType.class)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private StationCapabilities capabilities;
    
    @Type(JsonType.class)
    @Column(name = "diagnostics", columnDefinition = "jsonb")
    private StationDiagnostics diagnostics;
    
    // Security
    @Column(name = "certificate_fingerprint", length = 128)
    private String certificateFingerprint;
    
    @Column(name = "certificate_expires_at")
    private LocalDateTime certificateExpiresAt;
    
    // Relations
    @OneToMany(mappedBy = "chargingStation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Connector> connectors = new HashSet<>();
    
    @OneToMany(mappedBy = "chargingStation", fetch = FetchType.LAZY)
    private Set<ChargingSession> chargingSessions = new HashSet<>();
    
    @OneToMany(mappedBy = "chargingStation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<StationConfigurationItem> configurationItems = new HashSet<>();
}

public enum OCPPVersion {
    OCPP_1_5,
    OCPP_1_6,
    OCPP_2_0,
    OCPP_2_0_1
}

public enum OCPPStatus {
    AVAILABLE,
    PREPARING,
    CHARGING,
    SUSPENDED_EVSE,
    SUSPENDED_EV,
    FINISHING,
    RESERVED,
    UNAVAILABLE,
    FAULTED,
    OFFLINE
}

public enum StationStatus {
    ACTIVE,          // Station opérationnelle
    INACTIVE,        // Station désactivée
    MAINTENANCE,     // En maintenance
    FAULTY,          // En panne
    OFFLINE,         // Hors ligne
    DECOMMISSIONED   // Hors service définitif
}

public enum StationAvailability {
    OPERATIVE,
    INOPERATIVE,
    SCHEDULED
}

// Configuration complexe de la station
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StationConfiguration {
    private Map<String, String> ocppParameters;
    private NetworkConfiguration network;
    private SecurityConfiguration security;
    private DisplayConfiguration display;
    private Map<String, Object> manufacturerSpecific;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StationCapabilities {
    private Set<String> supportedFeatureProfiles;
    private Set<String> supportedConnectorTypes;
    private Integer maxPower;
    private Integer numberOfConnectors;
    private Boolean supportsReservation;
    private Boolean supportsSmartCharging;
    private Boolean supportsRemoteStart;
    private Set<String> supportedPaymentMethods;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StationDiagnostics {
    private LocalDateTime lastDiagnosticsRun;
    private Map<String, Object> systemHealth;
    private Set<String> activeErrors;
    private Set<String> warnings;
    private Map<String, Object> performanceMetrics;
}
```

#### 3.1.2 Connector Entity
```java
@Entity
@Table(name = "connectors",
       uniqueConstraints = @UniqueConstraint(columnNames = {"charging_station_id", "connector_id"}),
       indexes = {
           @Index(name = "idx_connectors_station", columnList = "charging_station_id"),
           @Index(name = "idx_connectors_status", columnList = "charging_station_id, status"),
           @Index(name = "idx_connectors_type", columnList = "connector_type")
       })
public class Connector extends AuditableEntity {
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charging_station_id", nullable = false)
    private ChargingStation chargingStation;
    
    @Column(name = "connector_id", nullable = false)
    @Min(1)
    private Integer connectorId; // 1-based connector number
    
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false)
    private ConnectorType connectorType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConnectorStatus status;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Column(name = "status_info", length = 255)
    private String statusInfo;
    
    @Column(name = "vendor_error_code", length = 50)
    private String vendorErrorCode;
    
    // Power capabilities
    @Column(name = "max_power")
    @Min(1)
    private Integer maxPower; // Watts
    
    @Column(name = "max_current")
    @Min(1)
    private Integer maxCurrent; // Ampères
    
    @Column(name = "voltage")
    @Min(1)
    private Integer voltage; // Volts
    
    @Column(name = "number_of_phases")
    @Min(1)
    @Max(3)
    private Integer numberOfPhases;
    
    // Current session
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_session_id")
    private ChargingSession currentSession;
    
    // Tariff information
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;
    
    // Configuration
    @Type(JsonType.class)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private ConnectorConfiguration configuration;
    
    // Relations
    @OneToMany(mappedBy = "connector", fetch = FetchType.LAZY)
    private Set<ChargingSession> chargingSessions = new HashSet<>();
}

public enum ConnectorType {
    CHADEMO,          // CHAdeMO
    IEC_62196_T1,     // Type 1 (J1772)
    IEC_62196_T2,     // Type 2 (Mennekes)
    IEC_62196_T3A,    // Type 3A (Scame)
    IEC_62196_T3C,    // Type 3C (Scame)
    DOMESTIC_A,       // Domestic socket type A
    DOMESTIC_B,       // Domestic socket type B
    DOMESTIC_C,       // Domestic socket type C
    DOMESTIC_D,       // Domestic socket type D
    DOMESTIC_E,       // Domestic socket type E
    DOMESTIC_F,       // Domestic socket type F
    DOMESTIC_G,       // Domestic socket type G
    DOMESTIC_H,       // Domestic socket type H
    DOMESTIC_I,       // Domestic socket type I
    DOMESTIC_J,       // Domestic socket type J
    DOMESTIC_K,       // Domestic socket type K
    DOMESTIC_L,       // Domestic socket type L
    IEC_60309_2_single_16,   // Industrial socket single phase 16A
    IEC_60309_2_three_16,    // Industrial socket three phase 16A
    IEC_60309_2_three_32,    // Industrial socket three phase 32A
    IEC_60309_2_three_64,    // Industrial socket three phase 64A
    IEC_62196_T1_COMBO,      // CCS Type 1
    IEC_62196_T2_COMBO,      // CCS Type 2
    TESLA_R,          // Tesla Roadster
    TESLA_S,          // Tesla Model S/X
    IEC_62196_T3A,    // Type 3A
    IEC_62196_T3C,    // Type 3C
    PANTOGRAPH_BOTTOM_UP,    // Pantograph bottom up
    PANTOGRAPH_TOP_DOWN,     // Pantograph top down
    WIRELESS,         // Wireless charging
    UNDETERMINED,     // Connector type cannot be determined
    UNKNOWN           // Unknown connector type
}

public enum ConnectorStatus {
    AVAILABLE,        // Connector is available for new sessions
    OCCUPIED,         // Connector is occupied by a charging session
    RESERVED,         // Connector is reserved
    UNAVAILABLE,      // Connector is unavailable
    FAULTED          // Connector is faulted
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorConfiguration {
    private Boolean isEnabled;
    private Integer priority; // Priority for load balancing
    private Map<String, String> customParameters;
    private ReservationConfiguration reservation;
    private LoadBalancingConfiguration loadBalancing;
}
```

---

## 4. Session Management

### 4.1 Charging Session

#### 4.1.1 ChargingSession Entity
```java
@Entity
@Table(name = "charging_sessions",
       indexes = {
           @Index(name = "idx_sessions_tenant_station", columnList = "tenant_id, charging_station_id"),
           @Index(name = "idx_sessions_user", columnList = "tenant_id, user_id"),
           @Index(name = "idx_sessions_status", columnList = "tenant_id, status"),
           @Index(name = "idx_sessions_dates", columnList = "tenant_id, start_timestamp, end_timestamp"),
           @Index(name = "idx_sessions_session_id", columnList = "tenant_id, session_id"),
           @Index(name = "idx_sessions_transaction", columnList = "tenant_id, transaction_id")
       })
public class ChargingSession extends AuditableEntity {
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charging_station_id", nullable = false)
    private ChargingStation chargingStation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_token_id")
    private AuthToken authToken;
    
    // Session identification
    @Column(name = "session_id", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String sessionId; // OCPP session ID
    
    @Column(name = "transaction_id", length = 100)
    @Size(max = 100)
    private String transactionId; // OCPP transaction ID
    
    @Column(name = "external_session_id", length = 255)
    @Size(max = 255)
    private String externalSessionId; // ID externe (OCPI, roaming)
    
    // Timestamps
    @Column(name = "start_timestamp")
    private LocalDateTime startTimestamp;
    
    @Column(name = "end_timestamp")
    private LocalDateTime endTimestamp;
    
    @Column(name = "authorization_timestamp")
    private LocalDateTime authorizationTimestamp;
    
    @Column(name = "plug_in_timestamp")
    private LocalDateTime plugInTimestamp;
    
    @Column(name = "plug_out_timestamp")
    private LocalDateTime plugOutTimestamp;
    
    // Energy measurements
    @Column(name = "start_value", precision = 10, scale = 3)
    private BigDecimal startValue; // kWh
    
    @Column(name = "end_value", precision = 10, scale = 3)
    private BigDecimal endValue; // kWh
    
    @Column(name = "total_energy", precision = 10, scale = 3)
    private BigDecimal totalEnergy; // kWh
    
    @Column(name = "max_power", precision = 8, scale = 3)
    private BigDecimal maxPower; // kW
    
    @Column(name = "avg_power", precision = 8, scale = 3)
    private BigDecimal avgPower; // kW
    
    // Status and state
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason")
    private StopReason stopReason;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Column(name = "error_description", length = 500)
    private String errorDescription;
    
    // Authorization
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method")
    private AuthMethod authMethod;
    
    @Column(name = "id_tag", length = 255)
    private String idTag; // Token utilisé pour l'auth
    
    // Billing and pricing
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;
    
    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;
    
    @Column(name = "currency", length = 3)
    @Size(max = 3)
    private String currency;
    
    @Type(JsonType.class)
    @Column(name = "billing_details", columnDefinition = "jsonb")
    private BillingDetails billingDetails;
    
    // OCPI integration
    @Column(name = "ocpi_partner_id")
    private UUID ocpiPartnerId;
    
    @Type(JsonType.class)
    @Column(name = "ocpi_data", columnDefinition = "jsonb")
    private OCPISessionData ocpiData;
    
    // Metadata and diagnostics
    @Type(JsonType.class)
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    private SessionMetadata sessionMetadata;
    
    @Type(JsonType.class)
    @Column(name = "diagnostics", columnDefinition = "jsonb")
    private SessionDiagnostics diagnostics;
    
    // Relations
    @OneToMany(mappedBy = "chargingSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<MeterValue> meterValues = new HashSet<>();
    
    @OneToMany(mappedBy = "chargingSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<SessionEvent> sessionEvents = new HashSet<>();
    
    @OneToOne(mappedBy = "chargingSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ChargeDetailRecord chargeDetailRecord;
}

public enum SessionStatus {
    INITIATED,        // Session demandée mais pas encore commencée
    AUTHORIZED,       // Utilisateur autorisé
    PREPARING,        // Préparation de la charge
    CHARGING,         // Charge en cours
    SUSPENDED_EV,     // Suspendue côté véhicule
    SUSPENDED_EVSE,   // Suspendue côté station
    FINISHING,        // Fin de charge en cours
    COMPLETED,        // Terminée avec succès
    INTERRUPTED,      // Interrompue
    CANCELLED,        // Annulée
    FAILED,           // Échouée
    RESERVED          // Réservée
}

public enum StopReason {
    EMERGENCY_STOP,
    EV_DISCONNECTED,
    HARD_RESET,
    LOCAL,
    OTHER,
    POWER_LOSS,
    REBOOT,
    REMOTE,
    SOFT_RESET,
    UNLOCK_COMMAND,
    DE_AUTHORIZED,
    ENERGY_LIMIT_REACHED,
    GROUND_FAULT,
    IMMEDIATE_RESET,
    LOCAL_OUT_OF_CREDIT,
    MASTER_PASS,
    OVERCURRENT_FAULT,
    POWER_QUALITY,
    SOC_LIMIT_REACHED,
    STOPPED_BY_EV,
    TIME_LIMIT_REACHED,
    TIMEOUT
}

public enum AuthMethod {
    RFID,
    NFC,
    QR_CODE,
    ISO15118,
    AUTOCHARGE,
    API,
    ROAMING,
    MANUAL,
    RESERVED
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingDetails {
    private List<BillingItem> items;
    private Map<String, BigDecimal> taxBreakdown;
    private String billingReference;
    private LocalDateTime billingTimestamp;
    private BillingStatus billingStatus;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OCPISessionData {
    private String cdrId;
    private String locationId;
    private String evseUid;
    private String connectorId;
    private String authorizationReference;
    private Map<String, Object> additionalData;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionMetadata {
    private String vehicleId;
    private String vehicleType;
    private Integer batteryCapacity;
    private Integer initialSoC;
    private Integer finalSoC;
    private Map<String, Object> customFields;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDiagnostics {
    private List<String> warnings;
    private List<String> errors;
    private Map<String, Object> performanceMetrics;
    private List<NetworkEvent> networkEvents;
    private QualityMetrics qualityMetrics;
}
```

#### 4.1.2 MeterValue Entity
```java
@Entity
@Table(name = "meter_values",
       indexes = {
           @Index(name = "idx_meter_session", columnList = "charging_session_id"),
           @Index(name = "idx_meter_timestamp", columnList = "charging_session_id, timestamp"),
           @Index(name = "idx_meter_measurand", columnList = "measurand, timestamp")
       })
public class MeterValue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charging_session_id", nullable = false)
    private ChargingSession chargingSession;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "measurand", nullable = false)
    private Measurand measurand;
    
    @Column(name = "value", nullable = false, precision = 15, scale = 6)
    private BigDecimal value;
    
    @Column(name = "unit", length = 20)
    private String unit;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "context")
    private ReadingContext context;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "format")
    private ValueFormat format;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "location")
    private MeasurementLocation location;
    
    @Column(name = "phase", length = 10)
    private String phase;
}

public enum Measurand {
    CURRENT_EXPORT,
    CURRENT_IMPORT,
    CURRENT_OFFERED,
    ENERGY_ACTIVE_EXPORT_REGISTER,
    ENERGY_ACTIVE_IMPORT_REGISTER,
    ENERGY_REACTIVE_EXPORT_REGISTER,
    ENERGY_REACTIVE_IMPORT_REGISTER,
    ENERGY_ACTIVE_EXPORT_INTERVAL,
    ENERGY_ACTIVE_IMPORT_INTERVAL,
    ENERGY_REACTIVE_EXPORT_INTERVAL,
    ENERGY_REACTIVE_IMPORT_INTERVAL,
    FREQUENCY,
    POWER_ACTIVE_EXPORT,
    POWER_ACTIVE_IMPORT,
    POWER_FACTOR,
    POWER_OFFERED,
    POWER_REACTIVE_EXPORT,
    POWER_REACTIVE_IMPORT,
    RPM,
    SOC,
    TEMPERATURE,
    VOLTAGE
}

public enum ReadingContext {
    INTERRUPTION_BEGIN,
    INTERRUPTION_END,
    OTHER,
    SAMPLE_CLOCK,
    SAMPLE_PERIODIC,
    TRANSACTION_BEGIN,
    TRANSACTION_END,
    TRIGGER
}

public enum ValueFormat {
    RAW,
    SIGNED_DATA
}

public enum MeasurementLocation {
    BODY,
    CABLE,
    EV,
    INLET,
    OUTLET
}
```

---

## 5. OCPI Hub Data Models

### 5.1 OCPI Partners

#### 5.1.1 OCPIPartner Entity
```java
@Entity
@Table(name = "ocpi_partners",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "country_code", "party_id"}),
       indexes = {
           @Index(name = "idx_ocpi_partners_tenant", columnList = "tenant_id"),
           @Index(name = "idx_ocpi_partners_status", columnList = "tenant_id, status"),
           @Index(name = "idx_ocpi_partners_role", columnList = "role")
       })
public class OCPIPartner extends AuditableEntity {
    
    @Column(name = "country_code", nullable = false, length = 2)
    @NotBlank
    @Size(min = 2, max = 2)
    private String countryCode;
    
    @Column(name = "party_id", nullable = false, length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String partyId;
    
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OCPIRole role;
    
    @Column(name = "business_details", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private BusinessDetails businessDetails;
    
    // OCPI credentials
    @Column(name = "credentials_token", length = 500)
    @Size(max = 500)
    private String credentialsToken;
    
    @Column(name = "credentials_url", length = 500)
    @Size(max = 500)
    private String credentialsUrl;
    
    @Column(name = "versions_url", length = 500)
    @Size(max = 500)
    private String versionsUrl;
    
    // Endpoints
    @Type(JsonType.class)
    @Column(name = "endpoints", columnDefinition = "jsonb")
    private Map<String, EndpointInfo> endpoints;
    
    // Status and configuration
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PartnerStatus status;
    
    @Column(name = "last_sync_timestamp")
    private LocalDateTime lastSyncTimestamp;
    
    @Column(name = "sync_frequency_minutes")
    private Integer syncFrequencyMinutes;
    
    @Type(JsonType.class)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private PartnerConfiguration configuration;
    
    // Relations
    @OneToMany(mappedBy = "ocpiPartner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<OCPILocation> locations = new HashSet<>();
    
    @OneToMany(mappedBy = "ocpiPartner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<OCPITariff> tariffs = new HashSet<>();
}

public enum OCPIRole {
    CPO,    // Charge Point Operator
    MSP,    // Mobility Service Provider  
    HUB,    // Hub operator
    NSP     // Navigation Service Provider
}

public enum PartnerStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION,
    BLOCKED
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessDetails {
    private String name;
    private String website;
    private Image logo;
    private String email;
    private String phone;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointInfo {
    private String url;
    private String interfaceRole;
    private String identifier;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartnerConfiguration {
    private Boolean autoSyncEnabled;
    private Set<String> supportedModules;
    private Map<String, Object> customSettings;
    private RateLimitConfiguration rateLimits;
}
```

### 5.2 OCPI Locations

#### 5.2.1 OCPILocation Entity
```java
@Entity
@Table(name = "ocpi_locations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "ocpi_partner_id", "location_id"}),
       indexes = {
           @Index(name = "idx_ocpi_locations_tenant", columnList = "tenant_id"),
           @Index(name = "idx_ocpi_locations_partner", columnList = "ocpi_partner_id"),
           @Index(name = "idx_ocpi_locations_country", columnList = "country_code"),
           @Index(name = "idx_ocpi_locations_updated", columnList = "last_updated")
       })
public class OCPILocation extends TenantEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocpi_partner_id")
    private OCPIPartner ocpiPartner;
    
    @Column(name = "location_id", nullable = false, length = 36)
    @NotBlank
    @Size(max = 36)
    private String locationId;
    
    @Column(name = "country_code", nullable = false, length = 2)
    @NotBlank
    @Size(min = 2, max = 2)
    private String countryCode;
    
    @Column(name = "party_id", nullable = false, length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String partyId;
    
    @Column(name = "name", length = 255)
    @Size(max = 255)
    private String name;
    
    @Column(name = "address", length = 500)
    @Size(max = 500)
    private String address;
    
    @Column(name = "city", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String city;
    
    @Column(name = "postal_code", length = 20)
    @Size(max = 20)
    private String postalCode;
    
    @Column(name = "state", length = 100)
    @Size(max = 100)
    private String state;
    
    @Column(name = "coordinates", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private GeoLocation coordinates;
    
    @Type(JsonType.class)
    @Column(name = "location_data", columnDefinition = "jsonb")
    private LocationData locationData;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "publish", nullable = false)
    private Boolean publish = true;
    
    // Relations
    @OneToMany(mappedBy = "ocpiLocation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<OCPIEVSE> evses = new HashSet<>();
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoLocation {
    @JsonProperty("latitude")
    private String latitude;  // Format: decimal degrees (4 decimal places)
    
    @JsonProperty("longitude") 
    private String longitude; // Format: decimal degrees (4 decimal places)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationData {
    private Set<String> relatedLocations;
    private Set<String> parkingTypes;
    private Set<Facility> facilities;
    private String timezone;
    private Hours openingTimes;
    private Boolean chargingWhenClosed;
    private Set<Image> images;
    private EnergyMix energyMix;
    private String operator;
    private String suboperator;
    private String owner;
}
```

---

## 6. Tarification et Facturation

### 6.1 Tariff Entity
```java
@Entity
@Table(name = "tariffs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "tariff_id"}),
       indexes = {
           @Index(name = "idx_tariffs_tenant", columnList = "tenant_id"),
           @Index(name = "idx_tariffs_status", columnList = "tenant_id, status"),
           @Index(name = "idx_tariffs_validity", columnList = "valid_from, valid_to")
       })
public class Tariff extends AuditableEntity {
    
    @Column(name = "tariff_id", nullable = false, length = 36)
    @NotBlank
    @Size(max = 36)
    private String tariffId;
    
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String name;
    
    @Column(name = "description", length = 1000)
    @Size(max = 1000)
    private String description;
    
    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
    
    @Type(JsonType.class)
    @Column(name = "tariff_elements", columnDefinition = "jsonb")
    private List<TariffElement> tariffElements;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_to")
    private LocalDateTime validTo;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TariffStatus status;
    
    @Type(JsonType.class)
    @Column(name = "restrictions", columnDefinition = "jsonb")
    private TariffRestrictions restrictions;
    
    // Relations
    @OneToMany(mappedBy = "tariff", fetch = FetchType.LAZY)
    private Set<Connector> connectors = new HashSet<>();
    
    @OneToMany(mappedBy = "tariff", fetch = FetchType.LAZY)
    private Set<ChargingSession> chargingSessions = new HashSet<>();
}

public enum TariffStatus {
    ACTIVE,
    INACTIVE,
    DRAFT,
    ARCHIVED
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TariffElement {
    private List<PriceComponent> priceComponents;
    private TariffRestrictions restrictions;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PriceComponent {
    private TariffDimensionType type;
    private BigDecimal price;
    private BigDecimal vatRate;
    private Integer stepSize;
}

public enum TariffDimensionType {
    ENERGY,      // kWh
    TIME,        // per hour
    FLAT,        // flat fee
    PARKING_TIME // parking per hour
}
```

---

## 7. Audit et Logging

### 7.1 AuditLog Entity
```java
@Entity
@Table(name = "audit_logs",
       indexes = {
           @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
           @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
           @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
           @Index(name = "idx_audit_user", columnList = "user_id"),
           @Index(name = "idx_audit_action", columnList = "action")
       })
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;
    
    @Column(name = "entity_type", length = 100)
    private String entityType;
    
    @Column(name = "entity_id")
    private UUID entityId;
    
    @Type(JsonType.class)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;
    
    @Type(JsonType.class)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private AuditSeverity severity;
    
    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

public enum AuditAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LOGIN,
    LOGOUT,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    SESSION_START,
    SESSION_STOP,
    COMMAND_SENT,
    CONFIGURATION_CHANGED,
    SYSTEM_EVENT,
    ERROR,
    SECURITY_EVENT
}

public enum AuditSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

---

## 8. Indexes et Performance

### 8.1 Index Strategy
```sql
-- Multi-tenant indexes (tous les tables ont tenant_id en premier)
CREATE INDEX CONCURRENTLY idx_{table}_tenant ON {table}(tenant_id);

-- Indexes composites pour les requêtes fréquentes
CREATE INDEX CONCURRENTLY idx_sessions_tenant_station_status 
ON charging_sessions(tenant_id, charging_station_id, status);

CREATE INDEX CONCURRENTLY idx_sessions_tenant_dates 
ON charging_sessions(tenant_id, start_timestamp, end_timestamp);

-- Indexes pour les recherches OCPI
CREATE INDEX CONCURRENTLY idx_ocpi_locations_country_party 
ON ocpi_locations(country_code, party_id, last_updated);

-- Indexes sur JSONB pour les requêtes sur configuration
CREATE INDEX CONCURRENTLY idx_stations_config_ocpp_version 
ON charging_stations USING GIN ((configuration->'ocppParameters'->>'ocppVersion'));

-- Indexes partiels pour améliorer les performances
CREATE INDEX CONCURRENTLY idx_sessions_active 
ON charging_sessions(tenant_id, charging_station_id) 
WHERE status IN ('CHARGING', 'PREPARING', 'AUTHORIZED');

CREATE INDEX CONCURRENTLY idx_tokens_active 
ON auth_tokens(tenant_id, token_value, token_type) 
WHERE is_active = true AND (expires_at IS NULL OR expires_at > NOW());
```

### 8.2 Partitioning Strategy
```sql
-- Partitioning pour les sessions par mois
CREATE TABLE charging_sessions_2025_01 PARTITION OF charging_sessions
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

-- Partitioning pour les meter_values par jour  
CREATE TABLE meter_values_2025_01_01 PARTITION OF meter_values
FOR VALUES FROM ('2025-01-01 00:00:00') TO ('2025-01-02 00:00:00');
```

---

## Next Steps

Ce modèle de données complet fournit :

✅ **Multi-tenant robuste** avec isolation performante  
✅ **OCPP/OCPI complet** avec toutes les entités nécessaires  
✅ **Audit trail** et compliance GDPR  
✅ **Performance** avec indexation optimisée  
✅ **Flexibilité** via JSONB pour extensibilité  

Veux-tu que je continue avec :
1. **APIs détaillées** (OpenAPI specs complètes)
2. **Architecture sécurité** (PKI, certificats) 
3. **Passer à Phase 3** (plan de développement)

Quelle priorité ?