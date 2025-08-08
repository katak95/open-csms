package com.opencsms.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndTenantId(String username, String tenantId);
    
    Optional<User> findByEmailAndTenantId(String email, String tenantId);
    
    boolean existsByUsernameAndTenantId(String username, String tenantId);
    
    boolean existsByEmailAndTenantId(String email, String tenantId);
    
    List<User> findByTenantIdAndActiveTrue(String tenantId);
    
    List<User> findByTenantIdAndActiveTrueAndDeletedFalse(String tenantId);
    
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.emailVerified = false AND u.emailVerificationSentAt < :before")
    List<User> findPendingEmailVerifications(@Param("tenantId") String tenantId, @Param("before") Instant before);
    
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.lockedUntil IS NOT NULL AND u.lockedUntil < :now")
    List<User> findExpiredLocks(@Param("tenantId") String tenantId, @Param("now") Instant now);
    
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.lastLoginAt < :before")
    List<User> findInactiveUsers(@Param("tenantId") String tenantId, @Param("before") Instant before);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.code = :roleCode AND u.tenantId = :tenantId")
    List<User> findByRoleCodeAndTenantId(@Param("roleCode") String roleCode, @Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.active = true AND u.deleted = false")
    long countActiveUsersByTenant(@Param("tenantId") String tenantId);
}