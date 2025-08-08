package com.opencsms.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;
    private String tenantId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        
        // Set test values using reflection
        ReflectionTestUtils.setField(jwtService, "secretKey", 
            "ZGV2ZWxvcG1lbnQtc2VjcmV0LWtleS1jaGFuZ2UtaW4tcHJvZHVjdGlvbg==");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800L);
        
        userDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
                
        tenantId = "test-tenant";
    }

    @Test
    void shouldGenerateTokenWithTenantId() {
        // When: Generating token with tenant ID
        String token = jwtService.generateToken(userDetails, tenantId);

        // Then: Token should be generated
        assertThat(token).isNotBlank();
        
        // And: Should contain username and tenant ID
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        assertThat(jwtService.extractTenantId(token)).isEqualTo("test-tenant");
    }

    @Test
    void shouldValidateToken() {
        // Given: Valid token
        String token = jwtService.generateToken(userDetails, tenantId);

        // When: Validating token
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Then: Token should be valid
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectInvalidToken() {
        // Given: Invalid token
        String invalidToken = "invalid.token.here";

        // When: Validating invalid token
        // Then: Should throw exception
        assertThatThrownBy(() -> jwtService.isTokenValid(invalidToken, userDetails))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldGenerateRefreshToken() {
        // When: Generating refresh token
        String refreshToken = jwtService.generateRefreshToken(userDetails, tenantId);

        // Then: Refresh token should be generated
        assertThat(refreshToken).isNotBlank();
        
        // And: Should contain username and tenant ID
        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("testuser");
        assertThat(jwtService.extractTenantId(refreshToken)).isEqualTo("test-tenant");
    }

    @Test
    void shouldDetectExpiredToken() {
        // Given: Service with short expiration
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1L); // Expired immediately
        
        String expiredToken = jwtService.generateToken(userDetails, tenantId);

        // When: Checking if token is expired
        boolean isExpired = jwtService.isTokenExpired(expiredToken);

        // Then: Token should be expired
        assertThat(isExpired).isTrue();
    }

    @Test
    void shouldExtractClaimsCorrectly() {
        // Given: Token with claims
        String token = jwtService.generateToken(userDetails, tenantId);

        // When: Extracting claims
        String username = jwtService.extractUsername(token);
        String extractedTenantId = jwtService.extractTenantId(token);

        // Then: Claims should be extracted correctly
        assertThat(username).isEqualTo("testuser");
        assertThat(extractedTenantId).isEqualTo("test-tenant");
    }
}