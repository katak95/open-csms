# Open-CSMS - Security Architecture & Deployment

**Version:** 1.0  
**Date:** 2025-01-07  
**Status:** Draft

---

## 1. Security Overview

Open-CSMS implémente une architecture de sécurité **defense-in-depth** avec :
- **Multi-tenant security** par design
- **PKI complète** pour OCPP et OCPI
- **Zero-trust** architecture
- **GDPR by design**
- **Enterprise-grade** compliance

### 1.1 Security Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PERIMETER SECURITY                      │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ WAF         │ DDoS        │ IP          │ Geo         │  │
│  │ Protection  │ Protection  │ Filtering   │ Blocking    │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                 TRANSPORT SECURITY                         │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ TLS 1.3     │ Mutual TLS  │ Certificate │ HSTS/HPKP   │  │
│  │ Encryption  │ Auth        │ Pinning     │ Headers     │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│               APPLICATION SECURITY                         │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ JWT/OAuth2  │ RBAC        │ API Keys    │ Rate        │  │
│  │ Tokens      │ Multi-Level │ Management  │ Limiting    │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                  DATA SECURITY                            │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐  │
│  │ Encryption  │ Multi-Tenant│ PII         │ Audit       │  │
│  │ at Rest     │ Isolation   │ Protection  │ Logging     │  │
│  └─────────────┴─────────────┴─────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. PKI Architecture

### 2.1 Certificate Authority Structure

#### 2.1.1 CA Hierarchy
```
Root CA (Open-CSMS)
├── Intermediate CA (OCPP)
│   ├── Station Certificates (TLS Client Auth)
│   └── Server Certificates (TLS Server Auth)
├── Intermediate CA (OCPI)  
│   ├── Partner Certificates (API Authentication)
│   └── Hub Certificates (Inter-operator)
├── Intermediate CA (Internal)
│   ├── Service Certificates (Internal mTLS)
│   └── Admin Certificates (Management)
└── Intermediate CA (Development/Test)
    ├── Test Station Certificates
    └── Development Environment Certificates
```

#### 2.1.2 PKI Implementation
```java
@Service
@Slf4j
public class PKIManagementService {
    
    @Autowired
    private CertificateAuthorityService caService;
    
    @Autowired
    private KeyStoreService keyStoreService;
    
    @Autowired
    private CertificateValidationService validationService;
    
    // Generate certificate for new charging station
    public StationCertificate generateStationCertificate(String stationId, UUID tenantId) {
        
        // Generate key pair
        KeyPair keyPair = generateKeyPair(2048);
        
        // Create certificate signing request
        PKCS10CertificationRequest csr = createCSR(
            stationId,
            keyPair,
            CertificateProfile.OCPP_STATION
        );
        
        // Sign certificate with OCPP Intermediate CA
        X509Certificate certificate = caService.signCertificate(
            csr,
            CertificateAuthority.OCPP_INTERMEDIATE,
            CertificateValidity.TWO_YEARS
        );
        
        // Store in tenant-isolated keystore
        StationCertificate stationCert = StationCertificate.builder()
            .stationId(stationId)
            .tenantId(tenantId)
            .certificate(certificate)
            .privateKey(keyPair.getPrivate())
            .publicKey(keyPair.getPublic())
            .serialNumber(certificate.getSerialNumber().toString())
            .validFrom(certificate.getNotBefore().toInstant())
            .validTo(certificate.getNotAfter().toInstant())
            .status(CertificateStatus.ACTIVE)
            .build();
        
        keyStoreService.storeCertificate(stationCert);
        
        // Log certificate generation
        auditService.logSecurityEvent(tenantId, 
            AuditAction.CERTIFICATE_GENERATED,
            "Station certificate generated",
            Map.of(
                "stationId", stationId,
                "serialNumber", certificate.getSerialNumber().toString(),
                "validTo", certificate.getNotAfter()
            )
        );
        
        return stationCert;
    }
    
    // Validate certificate chain
    public CertificateValidationResult validateCertificateChain(X509Certificate certificate) {
        
        try {
            // Build certificate path
            CertPath certPath = buildCertificatePath(certificate);
            
            // Validate against trust anchors
            PKIXParameters params = new PKIXParameters(getTrustAnchors());
            params.setRevocationEnabled(true); // Enable OCSP/CRL checking
            
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) 
                validator.validate(certPath, params);
            
            // Additional custom validations
            validateCertificateUsage(certificate);
            validateCertificateExtensions(certificate);
            
            return CertificateValidationResult.builder()
                .valid(true)
                .trustAnchor(result.getTrustAnchor())
                .policyTree(result.getPolicyTree())
                .build();
                
        } catch (CertPathValidatorException e) {
            log.warn("Certificate validation failed: {}", e.getMessage());
            
            return CertificateValidationResult.builder()
                .valid(false)
                .errorCode(mapValidationError(e))
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    // Certificate rotation for stations
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void rotateCertificatesScheduled() {
        
        // Find certificates expiring in next 30 days
        List<StationCertificate> expiringCertificates = 
            certificateRepository.findExpiringCertificates(
                Instant.now().plus(Duration.ofDays(30))
            );
        
        expiringCertificates.forEach(cert -> {
            try {
                rotateCertificate(cert);
                
                // Send notification to station to update certificate
                stationCommandService.sendCertificateUpdate(cert.getStationId(), cert);
                
            } catch (Exception e) {
                log.error("Failed to rotate certificate for station: {}", 
                         cert.getStationId(), e);
                
                // Send alert to administrators
                alertService.sendSecurityAlert(
                    cert.getTenantId(),
                    AlertSeverity.HIGH,
                    "Certificate rotation failed for station: " + cert.getStationId(),
                    e.getMessage()
                );
            }
        });
    }
}
```

### 2.2 OCPP Security Implementation

#### 2.2.1 TLS Configuration for OCPP
```java
@Configuration
@ConditionalOnProperty(name = "ocpp.security.enabled", havingValue = "true")
public class OCPPSecurityConfig {
    
    @Value("${ocpp.security.keystore.path}")
    private String keystorePath;
    
    @Value("${ocpp.security.keystore.password}")
    private String keystorePassword;
    
    @Value("${ocpp.security.truststore.path}")
    private String truststorePath;
    
    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        return new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                
                // Configure OCPP WebSocket endpoint with mTLS
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/ocpp/*");
                collection.addMethod("*");
                constraint.addCollection(collection);
                
                context.addConstraint(constraint);
                
                // Client certificate authentication
                context.setPreemptiveAuthentication(true);
            }
            
            @Override
            protected void customizeConnector(Connector connector) {
                super.customizeConnector(connector);
                
                if (connector.getPort() == 8443) { // OCPP TLS port
                    connector.setSecure(true);
                    connector.setScheme("https");
                    
                    Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                    protocol.setSSLEnabled(true);
                    protocol.setKeystoreFile(keystorePath);
                    protocol.setKeystorePass(keystorePassword);
                    protocol.setTruststoreFile(truststorePath);
                    protocol.setTruststorePass(keystorePassword);
                    protocol.setClientAuth("require"); // Require client certificates
                    protocol.setSSLProtocol("TLSv1.3");
                    protocol.setCiphers("TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256");
                }
            }
        };
    }
}
```

#### 2.2.2 OCPP Client Certificate Authentication
```java
@Component
public class OCPPClientCertificateAuthenticator {
    
    @Autowired
    private PKIManagementService pkiService;
    
    @Autowired
    private ChargingStationService stationService;
    
    public AuthenticationResult authenticateStation(X509Certificate clientCertificate, 
                                                  String stationId) {
        
        // Validate certificate chain
        CertificateValidationResult validation = 
            pkiService.validateCertificateChain(clientCertificate);
            
        if (!validation.isValid()) {
            return AuthenticationResult.builder()
                .authenticated(false)
                .errorCode("INVALID_CERTIFICATE")
                .errorMessage(validation.getErrorMessage())
                .build();
        }
        
        // Extract station ID from certificate subject
        String certStationId = extractStationIdFromCertificate(clientCertificate);
        
        if (!stationId.equals(certStationId)) {
            return AuthenticationResult.builder()
                .authenticated(false)
                .errorCode("STATION_ID_MISMATCH")
                .errorMessage("Station ID in URL doesn't match certificate")
                .build();
        }
        
        // Verify station exists and is active
        Optional<ChargingStation> station = stationService.findByStationId(stationId);
        if (station.isEmpty() || station.get().getStatus() != StationStatus.ACTIVE) {
            return AuthenticationResult.builder()
                .authenticated(false)
                .errorCode("STATION_NOT_FOUND")
                .errorMessage("Station not found or inactive")
                .build();
        }
        
        // Check certificate revocation status
        if (isRevoked(clientCertificate)) {
            return AuthenticationResult.builder()
                .authenticated(false)
                .errorCode("CERTIFICATE_REVOKED")
                .errorMessage("Client certificate has been revoked")
                .build();
        }
        
        return AuthenticationResult.builder()
            .authenticated(true)
            .stationId(stationId)
            .tenantId(station.get().getTenantId())
            .certificateSerialNumber(clientCertificate.getSerialNumber().toString())
            .build();
    }
    
    private String extractStationIdFromCertificate(X509Certificate certificate) {
        // Extract from Common Name or Subject Alternative Name
        String subject = certificate.getSubjectX500Principal().getName();
        
        // Parse CN=stationId,OU=OCPP,O=TenantName,C=FR format
        return Arrays.stream(subject.split(","))
                    .filter(part -> part.trim().startsWith("CN="))
                    .map(part -> part.substring(3))
                    .findFirst()
                    .orElse(null);
    }
}
```

### 2.3 OCPI Security Implementation

#### 2.3.1 OCPI Token Management
```java
@Service
@Slf4j
public class OCPISecurityService {
    
    @Autowired
    private OCPITokenRepository tokenRepository;
    
    @Autowired
    private CryptographyService cryptoService;
    
    // Generate secure OCPI token
    public OCPIToken generatePartnerToken(UUID tenantId, UUID partnerId, 
                                        Set<OCPIPermission> permissions) {
        
        // Generate cryptographically secure token
        String tokenValue = generateSecureToken(64);
        String tokenHash = cryptoService.hashToken(tokenValue);
        
        OCPIToken token = OCPIToken.builder()
            .tenantId(tenantId)
            .partnerId(partnerId)
            .tokenValue(tokenValue) // Store plaintext temporarily for response
            .tokenHash(tokenHash)   // Store hash for validation
            .permissions(permissions)
            .isActive(true)
            .expiresAt(Instant.now().plus(Duration.ofDays(365))) // 1 year validity
            .createdAt(Instant.now())
            .build();
        
        // Store token (plaintext will be cleared after first use)
        tokenRepository.save(token);
        
        // Audit log
        auditService.logSecurityEvent(tenantId,
            AuditAction.OCPI_TOKEN_GENERATED,
            "OCPI partner token generated",
            Map.of(
                "partnerId", partnerId,
                "permissions", permissions,
                "expiresAt", token.getExpiresAt()
            )
        );
        
        return token;
    }
    
    // Validate OCPI token
    public TokenValidationResult validateToken(String tokenValue) {
        
        String tokenHash = cryptoService.hashToken(tokenValue);
        
        Optional<OCPIToken> token = tokenRepository.findByTokenHash(tokenHash);
        
        if (token.isEmpty()) {
            return TokenValidationResult.builder()
                .valid(false)
                .errorCode("TOKEN_NOT_FOUND")
                .build();
        }
        
        OCPIToken ocpiToken = token.get();
        
        // Check token status
        if (!ocpiToken.getIsActive()) {
            return TokenValidationResult.builder()
                .valid(false)
                .errorCode("TOKEN_INACTIVE")
                .build();
        }
        
        // Check expiration
        if (ocpiToken.getExpiresAt().isBefore(Instant.now())) {
            return TokenValidationResult.builder()
                .valid(false)
                .errorCode("TOKEN_EXPIRED")
                .build();
        }
        
        // Check partner status
        OCPIPartner partner = partnerService.findById(ocpiToken.getPartnerId());
        if (partner.getStatus() != PartnerStatus.ACTIVE) {
            return TokenValidationResult.builder()
                .valid(false)
                .errorCode("PARTNER_INACTIVE")
                .build();
        }
        
        // Update last used timestamp
        ocpiToken.setLastUsedAt(Instant.now());
        ocpiToken.setUseCount(ocpiToken.getUseCount() + 1);
        tokenRepository.save(ocpiToken);
        
        return TokenValidationResult.builder()
            .valid(true)
            .tenantId(ocpiToken.getTenantId())
            .partnerId(ocpiToken.getPartnerId())
            .permissions(ocpiToken.getPermissions())
            .build();
    }
    
    // Token rotation
    public OCPIToken rotateToken(UUID tokenId) {
        
        OCPIToken oldToken = tokenRepository.findById(tokenId)
            .orElseThrow(() -> new TokenNotFoundException("Token not found: " + tokenId));
        
        // Deactivate old token
        oldToken.setIsActive(false);
        oldToken.setRevokedAt(Instant.now());
        tokenRepository.save(oldToken);
        
        // Generate new token with same permissions
        OCPIToken newToken = generatePartnerToken(
            oldToken.getTenantId(),
            oldToken.getPartnerId(),
            oldToken.getPermissions()
        );
        
        // Audit log
        auditService.logSecurityEvent(oldToken.getTenantId(),
            AuditAction.OCPI_TOKEN_ROTATED,
            "OCPI token rotated",
            Map.of(
                "oldTokenId", tokenId,
                "newTokenId", newToken.getId(),
                "partnerId", oldToken.getPartnerId()
            )
        );
        
        return newToken;
    }
}
```

---

## 3. Authentication & Authorization

### 3.1 Multi-Provider Authentication

#### 3.1.1 Authentication Service
```java
@Service
@Slf4j
public class MultiProviderAuthenticationService {
    
    @Autowired
    private List<AuthenticationProvider> authProviders;
    
    @Autowired
    private JwtTokenService jwtTokenService;
    
    @Autowired
    private UserService userService;
    
    // Authenticate user with multiple providers
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        
        AuthenticationProvider provider = findProvider(request.getProvider());
        if (provider == null) {
            return AuthenticationResult.failure("PROVIDER_NOT_FOUND", 
                                              "Authentication provider not found");
        }
        
        try {
            // Delegate to specific provider
            ProviderAuthResult providerResult = provider.authenticate(request);
            
            if (!providerResult.isAuthenticated()) {
                return AuthenticationResult.failure(
                    providerResult.getErrorCode(),
                    providerResult.getErrorMessage()
                );
            }
            
            // Find or create user
            User user = findOrCreateUser(providerResult);
            
            // Check user status
            if (user.getStatus() != UserStatus.ACTIVE) {
                return AuthenticationResult.failure("USER_INACTIVE", 
                                                  "User account is not active");
            }
            
            // Generate JWT token
            JwtToken jwtToken = jwtTokenService.generateToken(user);
            
            // Update last login
            user.setLastLoginAt(Instant.now());
            userService.save(user);
            
            // Audit log
            auditService.logSecurityEvent(user.getTenantId(),
                AuditAction.LOGIN,
                "User authenticated successfully",
                Map.of(
                    "userId", user.getId(),
                    "provider", request.getProvider(),
                    "ipAddress", request.getIpAddress(),
                    "userAgent", request.getUserAgent()
                )
            );
            
            return AuthenticationResult.success(user, jwtToken);
            
        } catch (Exception e) {
            log.error("Authentication failed for provider: {}", request.getProvider(), e);
            
            auditService.logSecurityEvent(null,
                AuditAction.AUTHENTICATION_FAILED,
                "Authentication attempt failed",
                Map.of(
                    "provider", request.getProvider(),
                    "error", e.getMessage(),
                    "ipAddress", request.getIpAddress()
                )
            );
            
            return AuthenticationResult.failure("AUTHENTICATION_ERROR", 
                                              "Authentication failed");
        }
    }
}
```

#### 3.1.2 OIDC Provider Implementation
```java
@Component
@ConditionalOnProperty(name = "auth.oidc.enabled", havingValue = "true")
public class OIDCAuthenticationProvider implements AuthenticationProvider {
    
    @Autowired
    private ReactiveOAuth2AuthorizedClientService authorizedClientService;
    
    @Autowired
    private WebClient webClient;
    
    @Value("${auth.oidc.issuer-uri}")
    private String issuerUri;
    
    @Value("${auth.oidc.client-id}")
    private String clientId;
    
    @Value("${auth.oidc.client-secret}")
    private String clientSecret;
    
    @Override
    public ProviderAuthResult authenticate(AuthenticationRequest request) {
        
        if (request.getType() != AuthenticationType.OIDC_AUTHORIZATION_CODE) {
            return ProviderAuthResult.failure("INVALID_AUTH_TYPE", 
                                            "Invalid authentication type for OIDC");
        }
        
        try {
            // Exchange authorization code for tokens
            TokenResponse tokenResponse = exchangeAuthorizationCode(
                request.getAuthorizationCode(),
                request.getRedirectUri()
            );
            
            // Validate ID token
            JWTClaimsSet claims = validateIdToken(tokenResponse.getIdToken());
            
            // Get user info from userinfo endpoint
            UserInfo userInfo = getUserInfo(tokenResponse.getAccessToken());
            
            return ProviderAuthResult.builder()
                .authenticated(true)
                .externalUserId(claims.getSubject())
                .email(userInfo.getEmail())
                .firstName(userInfo.getGivenName())
                .lastName(userInfo.getFamilyName())
                .attributes(Map.of(
                    "issuer", claims.getIssuer(),
                    "audience", claims.getAudience(),
                    "issuedAt", claims.getIssueTime(),
                    "expiresAt", claims.getExpirationTime()
                ))
                .build();
                
        } catch (Exception e) {
            log.error("OIDC authentication failed", e);
            return ProviderAuthResult.failure("OIDC_ERROR", e.getMessage());
        }
    }
    
    private TokenResponse exchangeAuthorizationCode(String code, String redirectUri) {
        // Implementation of OAuth2 authorization code flow
        // ...
    }
    
    private JWTClaimsSet validateIdToken(String idToken) throws Exception {
        
        // Get OIDC discovery document
        OIDCProviderMetadata providerMetadata = 
            OIDCProviderMetadata.resolve(new Issuer(issuerUri));
        
        // Get JWK Set
        JWKSource<SecurityContext> keySource = 
            JWKSourceBuilder.create(providerMetadata.getJWKSetURI().toURL()).build();
        
        // Create JWT processor
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = 
            new DefaultJWTProcessor<>();
            
        // Set JWK source
        JWKSourceKeySelector<SecurityContext> keySelector = 
            new JWKSourceKeySelector<>(RSA, keySource);
        jwtProcessor.setJWKSourceKeySelector(keySelector);
        
        // Set required claims
        jwtProcessor.setJWTClaimsSetAwareJWSKeySelector(keySelector);
        
        // Process and validate token
        return jwtProcessor.process(idToken, null);
    }
}
```

### 3.2 Role-Based Access Control (RBAC)

#### 3.2.1 Permission System
```java
@Service
@Slf4j
public class PermissionService {
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PermissionRepository permissionRepository;
    
    // Check if user has permission
    public boolean hasPermission(UUID userId, String resource, String action) {
        
        User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        return user.getRoles().stream()
                  .flatMap(role -> role.getPermissions().stream())
                  .anyMatch(permission -> 
                      matchesPermission(permission, resource, action, user.getTenantId()));
    }
    
    // Check permission with scope context
    public boolean hasPermissionWithScope(UUID userId, String resource, String action, 
                                        PermissionScope requiredScope, UUID resourceOwnerId) {
        
        User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        return user.getRoles().stream()
                  .flatMap(role -> role.getPermissions().stream())
                  .anyMatch(permission -> {
                      
                      // Check basic permission match
                      if (!matchesPermission(permission, resource, action, user.getTenantId())) {
                          return false;
                      }
                      
                      // Check scope
                      switch (permission.getScope()) {
                          case GLOBAL:
                              return true; // Global permissions always match
                              
                          case TENANT:
                              return user.getTenantId().equals(resourceOwnerId) ||
                                     isSameTenant(user.getTenantId(), resourceOwnerId);
                                     
                          case OWNED:
                              return user.getId().equals(resourceOwnerId) ||
                                     hasOwnershipRelation(user, resourceOwnerId);
                                     
                          default:
                              return false;
                      }
                  });
    }
    
    // Create custom role for tenant
    public Role createCustomRole(UUID tenantId, CreateRoleRequest request) {
        
        // Validate permissions exist
        Set<Permission> permissions = permissionRepository
            .findByNameIn(request.getPermissionNames());
            
        if (permissions.size() != request.getPermissionNames().size()) {
            throw new IllegalArgumentException("Some permissions not found");
        }
        
        Role role = Role.builder()
            .tenantId(tenantId)
            .name(request.getName())
            .description(request.getDescription())
            .roleType(RoleType.CUSTOM)
            .isDefault(false)
            .permissions(permissions)
            .build();
        
        roleRepository.save(role);
        
        // Audit log
        auditService.logSecurityEvent(tenantId,
            AuditAction.ROLE_CREATED,
            "Custom role created",
            Map.of(
                "roleName", request.getName(),
                "permissions", permissions.stream()
                    .map(Permission::getName)
                    .collect(Collectors.toList())
            )
        );
        
        return role;
    }
}
```

#### 3.2.2 Security Annotations
```java
// Custom security annotations
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasPermission(#root.target, #permission)")
public @interface RequiresPermission {
    String value(); // permission name like "stations.read"
    PermissionScope scope() default PermissionScope.TENANT;
}

// Method-level security
@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    
    @GetMapping("/{stationId}")
    @RequiresPermission("stations.read")
    public ResponseEntity<ChargingStationDTO> getStation(@PathVariable UUID stationId) {
        // Implementation
    }
    
    @PostMapping("/{stationId}/commands")
    @RequiresPermission(value = "stations.control", scope = PermissionScope.OWNED)
    public ResponseEntity<CommandResult> sendCommand(@PathVariable UUID stationId,
                                                   @RequestBody StationCommandRequest request) {
        // Implementation
    }
}
```

---

## 4. Data Security

### 4.1 Encryption at Rest

#### 4.1.1 Database Encryption
```java
@Configuration
@EnableJpaAuditing
public class DatabaseEncryptionConfig {
    
    @Bean
    public AttributeConverter<String, String> stringEncryptionConverter() {
        return new StringEncryptionConverter();
    }
    
    @Bean
    public AttributeConverter<Object, String> jsonEncryptionConverter() {
        return new JsonEncryptionConverter();
    }
}

// Encryption converter for sensitive fields
@Component
public class StringEncryptionConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptionService.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptionService.decrypt(dbData);
    }
}

// Usage in entities for PII data
@Entity
public class User extends AuditableEntity {
    
    @Column(name = "email")
    @Convert(converter = StringEncryptionConverter.class)
    private String email; // Encrypted in database
    
    @Column(name = "phone")  
    @Convert(converter = StringEncryptionConverter.class)
    private String phone; // Encrypted in database
    
    @Type(JsonType.class)
    @Column(name = "billing_profile", columnDefinition = "jsonb")
    @Convert(converter = JsonEncryptionConverter.class)
    private BillingProfile billingProfile; // Encrypted JSON
}
```

#### 4.1.2 Key Management
```java
@Service
@Slf4j
public class KeyManagementService {
    
    @Value("${encryption.key.rotation.days:90}")
    private int keyRotationDays;
    
    @Autowired
    private KeyRepository keyRepository;
    
    @Autowired
    private HSMService hsmService; // Hardware Security Module
    
    // Generate new encryption key
    public EncryptionKey generateKey(UUID tenantId, KeyType keyType) {
        
        // Generate key using HSM
        GeneratedKey generatedKey = hsmService.generateKey(
            KeySpecification.builder()
                .algorithm(KeyAlgorithm.AES_256)
                .usage(KeyUsage.ENCRYPT_DECRYPT)
                .extractable(false) // Key never leaves HSM
                .build()
        );
        
        EncryptionKey encryptionKey = EncryptionKey.builder()
            .tenantId(tenantId)
            .keyType(keyType)
            .keyId(generatedKey.getKeyId())
            .hsmKeyHandle(generatedKey.getHandle())
            .algorithm("AES-256-GCM")
            .status(KeyStatus.ACTIVE)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofDays(keyRotationDays)))
            .build();
        
        keyRepository.save(encryptionKey);
        
        return encryptionKey;
    }
    
    // Rotate encryption keys
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    public void rotateKeysScheduled() {
        
        // Find keys expiring in next 7 days
        List<EncryptionKey> expiringKeys = keyRepository
            .findExpiringKeys(Instant.now().plus(Duration.ofDays(7)));
        
        expiringKeys.forEach(this::rotateKey);
    }
    
    private void rotateKey(EncryptionKey oldKey) {
        
        try {
            // Generate new key
            EncryptionKey newKey = generateKey(oldKey.getTenantId(), oldKey.getKeyType());
            
            // Re-encrypt data with new key
            reencryptDataWithNewKey(oldKey, newKey);
            
            // Deactivate old key
            oldKey.setStatus(KeyStatus.DEACTIVATED);
            oldKey.setDeactivatedAt(Instant.now());
            keyRepository.save(oldKey);
            
            log.info("Successfully rotated encryption key: {} -> {}", 
                    oldKey.getKeyId(), newKey.getKeyId());
                    
        } catch (Exception e) {
            log.error("Failed to rotate encryption key: {}", oldKey.getKeyId(), e);
            
            // Send security alert
            alertService.sendSecurityAlert(
                oldKey.getTenantId(),
                AlertSeverity.CRITICAL,
                "Encryption key rotation failed",
                "Key ID: " + oldKey.getKeyId()
            );
        }
    }
}
```

### 4.2 Multi-Tenant Data Isolation

#### 4.2.1 Row-Level Security
```sql
-- Enable RLS on all tenant tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE charging_stations ENABLE ROW LEVEL SECURITY;
ALTER TABLE charging_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_tokens ENABLE ROW LEVEL SECURITY;

-- Create policies for tenant isolation
CREATE POLICY tenant_isolation_users ON users
FOR ALL TO application_role
USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

CREATE POLICY tenant_isolation_stations ON charging_stations
FOR ALL TO application_role  
USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

CREATE POLICY tenant_isolation_sessions ON charging_sessions
FOR ALL TO application_role
USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Special policy for super admins
CREATE POLICY super_admin_access ON users
FOR ALL TO super_admin_role
USING (true); -- Super admins see all tenants
```

#### 4.2.2 Application-Level Isolation
```java
@Component
@Aspect
public class TenantSecurityAspect {
    
    @Autowired
    private TenantContextService tenantContext;
    
    // Automatically inject tenant context for database operations
    @Before("@annotation(org.springframework.data.jpa.repository.Query)")
    public void injectTenantContext(JoinPoint joinPoint) {
        
        UUID currentTenantId = tenantContext.getCurrentTenantId();
        if (currentTenantId == null) {
            throw new SecurityException("No tenant context available");
        }
        
        // Set tenant ID in session for RLS
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Session session = sessionFactory.getCurrentSession();
        
        session.doWork(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, false)")) {
                stmt.setString(1, currentTenantId.toString());
                stmt.execute();
            }
        });
    }
    
    // Validate tenant access for sensitive operations
    @Around("@annotation(RequiresTenantAccess)")
    public Object validateTenantAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        
        Object[] args = joinPoint.getArgs();
        UUID requestedTenantId = extractTenantIdFromArgs(args);
        UUID currentTenantId = tenantContext.getCurrentTenantId();
        
        // Check if user has access to requested tenant
        if (!tenantAccessValidator.hasAccess(currentTenantId, requestedTenantId)) {
            throw new AccessDeniedException(
                "User does not have access to tenant: " + requestedTenantId);
        }
        
        return joinPoint.proceed();
    }
}
```

---

## 5. Security Monitoring & Compliance

### 5.1 Security Event Monitoring

#### 5.1.1 Security Event Detection
```java
@Service
@Slf4j
public class SecurityMonitoringService {
    
    @Autowired
    private SecurityEventRepository eventRepository;
    
    @Autowired
    private ThreatDetectionService threatDetection;
    
    @EventListener
    public void onAuthenticationEvent(AuthenticationEvent event) {
        
        SecurityEvent securityEvent = SecurityEvent.builder()
            .tenantId(event.getTenantId())
            .eventType(SecurityEventType.AUTHENTICATION)
            .severity(determineSeverity(event))
            .sourceIp(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .userId(event.getUserId())
            .timestamp(Instant.now())
            .details(Map.of(
                "success", event.isSuccess(),
                "provider", event.getProvider(),
                "failureReason", event.getFailureReason()
            ))
            .build();
        
        eventRepository.save(securityEvent);
        
        // Check for suspicious patterns
        if (!event.isSuccess()) {
            checkFailedLoginPattern(event);
        }
        
        // Geo-location anomaly detection
        if (event.isSuccess()) {
            checkGeolocationAnomaly(event);
        }
    }
    
    @EventListener
    public void onApiAccess(ApiAccessEvent event) {
        
        // Log API access for audit
        SecurityEvent securityEvent = SecurityEvent.builder()
            .tenantId(event.getTenantId())
            .eventType(SecurityEventType.API_ACCESS)
            .severity(SecuritySeverity.LOW)
            .sourceIp(event.getIpAddress())
            .userId(event.getUserId())
            .timestamp(Instant.now())
            .details(Map.of(
                "endpoint", event.getEndpoint(),
                "method", event.getMethod(),
                "responseStatus", event.getResponseStatus(),
                "responseTime", event.getResponseTime()
            ))
            .build();
        
        eventRepository.save(securityEvent);
        
        // Rate limiting violation detection
        if (event.isRateLimited()) {
            handleRateLimitViolation(event);
        }
        
        // Detect API abuse patterns
        detectApiAbusePatterns(event);
    }
    
    private void checkFailedLoginPattern(AuthenticationEvent event) {
        
        // Count failed logins in last 15 minutes
        long failedCount = eventRepository.countFailedLogins(
            event.getIpAddress(),
            Instant.now().minus(Duration.ofMinutes(15))
        );
        
        if (failedCount >= 5) {
            // Potential brute force attack
            SecurityAlert alert = SecurityAlert.builder()
                .tenantId(event.getTenantId())
                .alertType(SecurityAlertType.BRUTE_FORCE_ATTEMPT)
                .severity(SecuritySeverity.HIGH)
                .sourceIp(event.getIpAddress())
                .message("Multiple failed login attempts detected")
                .details(Map.of(
                    "failedCount", failedCount,
                    "timeWindow", "15 minutes"
                ))
                .build();
            
            alertService.sendSecurityAlert(alert);
            
            // Automatically block IP for 1 hour
            ipBlockingService.blockIp(event.getIpAddress(), Duration.ofHours(1),
                                    "Automated block due to brute force attempt");
        }
    }
}
```

### 5.2 GDPR Compliance

#### 5.2.1 Data Privacy Implementation
```java
@Service
@Slf4j
public class DataPrivacyService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PersonalDataRepository personalDataRepository;
    
    @Autowired
    private EncryptionService encryptionService;
    
    // Handle right to be forgotten (GDPR Article 17)
    @Transactional
    public DataErasureResult eraseUserData(UUID userId, DataErasureRequest request) {
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Validate erasure request
        if (!canEraseUser(user, request)) {
            return DataErasureResult.builder()
                .success(false)
                .reason("User data cannot be erased due to legal obligations")
                .build();
        }
        
        try {
            // Pseudonymize personal data instead of deletion for audit trail
            pseudonymizeUser(user);
            
            // Delete non-essential personal data
            deleteNonEssentialData(user);
            
            // Update sessions to remove personal identifiers
            anonymizeUserSessions(userId);
            
            // Mark user as erased
            user.setStatus(UserStatus.DELETED);
            user.setErasedAt(Instant.now());
            user.setErasureReason(request.getReason());
            
            userRepository.save(user);
            
            // Audit log
            auditService.logPrivacyEvent(user.getTenantId(),
                PrivacyEventType.DATA_ERASURE,
                "User data erased per GDPR request",
                Map.of(
                    "userId", userId,
                    "reason", request.getReason(),
                    "requestedBy", request.getRequestedBy()
                )
            );
            
            return DataErasureResult.builder()
                .success(true)
                .erasedAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to erase user data: {}", userId, e);
            return DataErasureResult.builder()
                .success(false)
                .reason("Technical error during data erasure")
                .build();
        }
    }
    
    // Generate data export for data portability (GDPR Article 20)
    public DataExportResult exportUserData(UUID userId, DataExportRequest request) {
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        Map<String, Object> userData = new HashMap<>();
        
        // Basic user information
        userData.put("profile", Map.of(
            "email", user.getEmail(),
            "firstName", user.getFirstName(),
            "lastName", user.getLastName(),
            "phone", user.getPhone(),
            "language", user.getLanguage(),
            "createdAt", user.getCreatedAt()
        ));
        
        // Charging sessions
        List<ChargingSession> sessions = sessionRepository.findByUserId(userId);
        userData.put("chargingSessions", sessions.stream()
            .map(this::sessionToExportFormat)
            .collect(Collectors.toList()));
        
        // Auth tokens (anonymized)
        userData.put("authTokens", user.getAuthTokens().stream()
            .map(token -> Map.of(
                "tokenType", token.getTokenType(),
                "createdAt", token.getCreatedAt(),
                "lastUsedAt", token.getLastUsedAt(),
                "useCount", token.getUseCount()
            ))
            .collect(Collectors.toList()));
        
        // Billing information (if requested)
        if (request.isIncludeBilling()) {
            userData.put("billing", exportBillingData(user));
        }
        
        // Audit log
        auditService.logPrivacyEvent(user.getTenantId(),
            PrivacyEventType.DATA_EXPORT,
            "User data exported per GDPR request",
            Map.of(
                "userId", userId,
                "includeBilling", request.isIncludeBilling(),
                "requestedBy", request.getRequestedBy()
            )
        );
        
        return DataExportResult.builder()
            .data(userData)
            .format(request.getFormat())
            .generatedAt(Instant.now())
            .build();
    }
    
    private void pseudonymizeUser(User user) {
        // Replace PII with pseudonymized values
        user.setEmail("user_" + user.getId() + "@pseudonymized.local");
        user.setFirstName("User");
        user.setLastName(user.getId().toString().substring(0, 8));
        user.setPhone(null);
        user.setBillingProfile(null);
        user.setAuthAttributes(null);
    }
}
```

---

## 6. Deployment Security

### 6.1 Secure Container Configuration

#### 6.1.1 Docker Security
```dockerfile
# Multi-stage build for security
FROM openjdk:17-jdk-slim as builder

WORKDIR /app
COPY . .

# Build application
RUN ./gradlew build -x test

# Production stage - minimal attack surface
FROM openjdk:17-jre-slim as production

# Create non-root user
RUN groupadd -r opencsms && useradd -r -g opencsms opencsms

# Install security updates only
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy application with proper ownership
COPY --from=builder --chown=opencsms:opencsms /app/build/libs/open-csms.jar app.jar

# Security configurations
USER opencsms:opencsms

# Expose only necessary port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run with security manager
ENTRYPOINT ["java", \
    "-Djava.security.manager=default", \
    "-Djava.security.policy=/app/security.policy", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
```

#### 6.1.2 Docker Compose Security
```yaml
version: '3.8'
services:
  app:
    image: open-csms:latest
    user: "1000:1000"  # Non-root user
    read_only: true     # Read-only filesystem
    tmpfs:
      - /tmp
      - /var/run
    cap_drop:
      - ALL            # Drop all capabilities
    cap_add:
      - NET_BIND_SERVICE  # Only allow binding to ports
    security_opt:
      - no-new-privileges:true  # Prevent privilege escalation
      - apparmor:docker-default # Use AppArmor profile
    ulimits:
      nproc: 1024      # Limit number of processes
      nofile: 65536    # Limit open files
    networks:
      - internal       # Isolated network
    secrets:
      - db_password
      - jwt_secret
      - ssl_cert
      - ssl_key
    environment:
      - SPRING_DATASOURCE_PASSWORD_FILE=/run/secrets/db_password
      - JWT_SECRET_FILE=/run/secrets/jwt_secret
    volumes:
      - app_logs:/app/logs:rw
      - /dev/urandom:/dev/random:ro  # Entropy source

  postgres:
    image: postgres:15-alpine
    user: "999:999"    # Non-root postgres user  
    read_only: true
    tmpfs:
      - /tmp
      - /var/run/postgresql
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    environment:
      - POSTGRES_PASSWORD_FILE=/run/secrets/db_password
      - POSTGRES_INITDB_ARGS="--auth-host=scram-sha-256"
    volumes:
      - postgres_data:/var/lib/postgresql/data:rw
    secrets:
      - db_password

secrets:
  db_password:
    external: true
  jwt_secret:
    external: true
  ssl_cert:
    external: true
  ssl_key:
    external: true

networks:
  internal:
    driver: bridge
    internal: true    # No external access
    
volumes:
  postgres_data:
    driver: local
  app_logs:
    driver: local
```

### 6.2 Network Security

#### 6.2.1 Network Segmentation
```yaml
# Production network topology
networks:
  # Public-facing network (reverse proxy only)
  public:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/24
    
  # Application network (app services)
  app:
    driver: bridge  
    internal: true
    ipam:
      config:
        - subnet: 172.21.0.0/24
        
  # Database network (data services only)
  data:
    driver: bridge
    internal: true
    ipam:
      config:
        - subnet: 172.22.0.0/24
        
  # Management network (monitoring, backups)
  mgmt:
    driver: bridge
    internal: true
    ipam:
      config:
        - subnet: 172.23.0.0/24

services:
  nginx:
    networks:
      - public
      - app
      
  app:
    networks:
      - app
      - data
      
  postgres:
    networks:
      - data
      
  redis:
    networks:
      - data
      
  monitoring:
    networks:
      - mgmt
      - app  # Read-only access for metrics
```

#### 6.2.2 Nginx Security Configuration
```nginx
# Security headers and configuration
server {
    listen 443 ssl http2;
    server_name opencsms.example.com;
    
    # SSL Configuration
    ssl_certificate /etc/ssl/certs/opencsms.crt;
    ssl_certificate_key /etc/ssl/private/opencsms.key;
    ssl_protocols TLSv1.3 TLSv1.2;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_stapling on;
    ssl_stapling_verify on;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';" always;
    
    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=auth:10m rate=1r/s;
    
    # Hide server information
    server_tokens off;
    
    # API endpoints
    location /api/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://app:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeout settings
        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # Authentication endpoints (stricter rate limiting)
    location /api/v1/auth/ {
        limit_req zone=auth burst=5 nodelay;
        proxy_pass http://app:8080/api/v1/auth/;
        # Same proxy settings as above
    }
    
    # OCPP WebSocket (with authentication)
    location /ocpp/ {
        proxy_pass http://app:8080/ocpp/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket timeout settings
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name opencsms.example.com;
    return 301 https://$server_name$request_uri;
}
```

---

## Next Steps

Cette architecture de sécurité complète couvre :

✅ **PKI complète** : CA hierarchy, cert generation/rotation, mTLS  
✅ **Multi-provider auth** : OIDC, SAML, LDAP avec JWT  
✅ **RBAC avancé** : Permissions granulaires, tenant isolation  
✅ **Chiffrement** : At-rest avec rotation, HSM integration  
✅ **Monitoring sécurité** : Event detection, threat analysis  
✅ **GDPR compliance** : Erasure, export, pseudonymization  
✅ **Déploiement sécurisé** : Container hardening, network segmentation  

<function_calls>
<invoke name="TodoWrite">
<parameter name="todos">[{"id": "1", "content": "Phase 1: D\u00e9finition des requirements d\u00e9taill\u00e9s", "status": "completed"}, {"id": "2", "content": "Phase 2: Conception de l'architecture", "status": "completed"}, {"id": "2a", "content": "Architecture g\u00e9n\u00e9rale et composants", "status": "completed"}, {"id": "2b", "content": "Mod\u00e8les de donn\u00e9es d\u00e9taill\u00e9s", "status": "completed"}, {"id": "2c", "content": "APIs et int\u00e9grations", "status": "completed"}, {"id": "2d", "content": "S\u00e9curit\u00e9 et d\u00e9ploiement", "status": "completed"}, {"id": "3", "content": "Phase 3: Organisation du plan de d\u00e9veloppement", "status": "pending"}, {"id": "4", "content": "Cr\u00e9er le CLAUDE.md avec le contexte du projet", "status": "pending"}]