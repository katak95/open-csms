package com.opencsms.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantFilter.
 */
@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private FilterChain filterChain;

    private TenantFilter tenantFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tenantFilter = new TenantFilter(tenantResolver);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldExtractTenantFromHeader() throws Exception {
        // Given: Request with tenant header
        request.addHeader("X-Tenant-ID", "test-tenant");
        request.setRequestURI("/api/test");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be set
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("test-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldExtractTenantFromParameter() throws Exception {
        // Given: Request with tenant parameter
        request.addParameter("tenantId", "test-tenant");
        request.setRequestURI("/api/test");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be set
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("test-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseDomainResolution() throws Exception {
        // Given: Request without header/param but domain resolution available
        request.setRequestURI("/api/test");
        when(tenantResolver.resolveFromDomain(request)).thenReturn("domain-tenant");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be set from domain
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("domain-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseTokenResolution() throws Exception {
        // Given: Request with JWT token containing tenant
        request.setRequestURI("/api/test");
        when(tenantResolver.resolveFromDomain(request)).thenReturn(null);
        when(tenantResolver.resolveFromToken(request)).thenReturn("token-tenant");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be set from token
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("token-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUsePathResolution() throws Exception {
        // Given: Request with tenant in path
        request.setRequestURI("/api/tenants/path-tenant/stations");
        when(tenantResolver.resolveFromDomain(request)).thenReturn(null);
        when(tenantResolver.resolveFromToken(request)).thenReturn(null);
        when(tenantResolver.resolveFromPath(request)).thenReturn("path-tenant");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be set from path
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("path-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowExcludedPaths() throws Exception {
        // Given: Request to excluded path
        request.setRequestURI("/actuator/health");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Should proceed without tenant context
        assertThat(TenantContext.getCurrentTenantOrNull()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnBadRequestWhenNoTenant() throws Exception {
        // Given: Request without tenant to protected path
        request.setRequestURI("/api/protected");
        request.setMethod("GET");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Should return bad request
        assertThat(response.getStatus()).isEqualTo(400);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldClearTenantContextAfterRequest() throws Exception {
        // Given: Request with tenant
        request.addHeader("X-Tenant-ID", "test-tenant");
        request.setRequestURI("/api/test");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Tenant context should be cleared after processing
        assertThat(TenantContext.getCurrentTenantOrNull()).isNull();
    }

    @Test
    void shouldPrioritizeHeaderOverParameter() throws Exception {
        // Given: Request with both header and parameter
        request.addHeader("X-Tenant-ID", "header-tenant");
        request.addParameter("tenantId", "param-tenant");
        request.setRequestURI("/api/test");

        // When: Processing request
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then: Should use header value
        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo("header-tenant");
        verify(filterChain).doFilter(request, response);
    }
}