package com.opencsms.core.tenant;

import com.opencsms.domain.tenant.Tenant;
import com.opencsms.domain.user.User;
import com.opencsms.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for multi-tenant isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class MultiTenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    private Tenant tenant1;
    private Tenant tenant2;

    @BeforeEach
    void setUp() {
        // Create test tenants
        tenant1 = Tenant.builder()
                .code("test-tenant-1")
                .name("Test Tenant 1")
                .tenantType(Tenant.TenantType.CPO)
                .build();
        tenant1 = tenantRepository.save(tenant1);

        tenant2 = Tenant.builder()
                .code("test-tenant-2")
                .name("Test Tenant 2")
                .tenantType(Tenant.TenantType.EMSP)
                .build();
        tenant2 = tenantRepository.save(tenant2);
    }

    @Test
    void shouldIsolateUsersByTenant() {
        // Given: Users in different tenants
        TenantContext.setCurrentTenant("test-tenant-1");
        User user1 = User.builder()
                .username("user1")
                .email("user1@test.com")
                .tenantId("test-tenant-1")
                .build();
        userRepository.save(user1);

        TenantContext.setCurrentTenant("test-tenant-2");
        User user2 = User.builder()
                .username("user2")
                .email("user2@test.com")
                .tenantId("test-tenant-2")
                .build();
        userRepository.save(user2);

        // When: Querying users from tenant 1
        TenantContext.setCurrentTenant("test-tenant-1");
        var tenant1Users = userRepository.findByTenantIdAndActiveTrue("test-tenant-1");

        // Then: Should only see tenant 1 users
        assertThat(tenant1Users).hasSize(1);
        assertThat(tenant1Users.get(0).getUsername()).isEqualTo("user1");

        // When: Querying users from tenant 2
        TenantContext.setCurrentTenant("test-tenant-2");
        var tenant2Users = userRepository.findByTenantIdAndActiveTrue("test-tenant-2");

        // Then: Should only see tenant 2 users
        assertThat(tenant2Users).hasSize(1);
        assertThat(tenant2Users.get(0).getUsername()).isEqualTo("user2");
    }

    @Test
    void shouldPreventCrossTenantDataAccess() {
        // Given: User created in tenant 1
        TenantContext.setCurrentTenant("test-tenant-1");
        User user1 = User.builder()
                .username("user1")
                .email("user1@test.com")
                .tenantId("test-tenant-1")
                .build();
        user1 = userRepository.save(user1);

        // When: Trying to access from tenant 2 context
        TenantContext.setCurrentTenant("test-tenant-2");
        var foundUser = userRepository.findByUsernameAndTenantId("user1", "test-tenant-2");

        // Then: Should not find the user
        assertThat(foundUser).isEmpty();
    }

    @Test
    void shouldEnforceTenantIdOnSave() {
        // Given: Current tenant context
        TenantContext.setCurrentTenant("test-tenant-1");

        // When: Creating user without explicit tenant ID
        User user = User.builder()
                .username("testuser")
                .email("test@test.com")
                .build();
        
        // The BaseEntity should auto-set tenant ID from context
        User savedUser = userRepository.save(user);

        // Then: Tenant ID should be automatically set
        assertThat(savedUser.getTenantId()).isEqualTo("test-tenant-1");
    }

    @Test
    void shouldPreventTenantIdModification() {
        // Given: User in tenant 1
        TenantContext.setCurrentTenant("test-tenant-1");
        User user = User.builder()
                .username("testuser")
                .email("test@test.com")
                .tenantId("test-tenant-1")
                .build();
        user = userRepository.save(user);

        // When: Attempting to change tenant ID
        user.setTenantId("test-tenant-2");
        
        // Then: Should throw exception (handled by Hibernate interceptor)
        assertThatThrownBy(() -> userRepository.save(user))
                .hasMessageContaining("Tenant ID cannot be changed");
    }

    @Test
    void shouldSupportTenantSwitching() {
        // Given: Users in different tenants
        TenantContext.setCurrentTenant("test-tenant-1");
        User user1 = User.builder()
                .username("user1")
                .email("user1@test.com")
                .tenantId("test-tenant-1")
                .build();
        userRepository.save(user1);

        TenantContext.setCurrentTenant("test-tenant-2");
        User user2 = User.builder()
                .username("user2")
                .email("user2@test.com")
                .tenantId("test-tenant-2")
                .build();
        userRepository.save(user2);

        // When: Switching between tenants
        TenantContext.setCurrentTenant("test-tenant-1");
        long tenant1Count = userRepository.countActiveUsersByTenant("test-tenant-1");

        TenantContext.setCurrentTenant("test-tenant-2");
        long tenant2Count = userRepository.countActiveUsersByTenant("test-tenant-2");

        // Then: Should see correct counts for each tenant
        assertThat(tenant1Count).isEqualTo(1);
        assertThat(tenant2Count).isEqualTo(1);
    }

    @Test
    void shouldClearTenantContext() {
        // Given: Tenant context set
        TenantContext.setCurrentTenant("test-tenant-1");
        assertThat(TenantContext.hasTenant()).isTrue();

        // When: Clearing context
        TenantContext.clear();

        // Then: Context should be empty
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenNoTenantContext() {
        // Given: No tenant context
        TenantContext.clear();

        // When: Trying to get current tenant
        // Then: Should throw exception
        assertThatThrownBy(() -> TenantContext.getCurrentTenant())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant set in current context");
    }

    @Test
    void shouldValidateTenantExistence() {
        // Given: Valid tenant
        TenantContext.setCurrentTenant("test-tenant-1");
        
        // When: Validating existing tenant
        // Then: Should not throw
        assertThatCode(() -> tenantService.validateCurrentTenant())
                .doesNotThrowAnyException();

        // When: Setting invalid tenant
        TenantContext.setCurrentTenant("non-existent-tenant");
        
        // Then: Validation should fail
        assertThatThrownBy(() -> tenantService.validateCurrentTenant())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid or inactive tenant");
    }
}