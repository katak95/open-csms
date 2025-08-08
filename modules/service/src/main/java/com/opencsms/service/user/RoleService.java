package com.opencsms.service.user;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.user.Permission;
import com.opencsms.domain.user.Role;
import com.opencsms.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for role-based access control (RBAC) management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserService userService;

    /**
     * Create a new role.
     */
    @Transactional
    public Role createRole(String code, String name, String description, boolean systemRole) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating role: {} for tenant: {}", code, tenantId);
        
        // Check for duplicate code
        if (roleRepository.existsByCodeAndTenantId(code, tenantId)) {
            throw new RoleServiceException("Role code already exists: " + code);
        }
        
        Role role = Role.builder()
            .code(code)
            .name(name)
            .description(description)
            .systemRole(systemRole)
            .tenantId(tenantId)
            .build();
        
        Role savedRole = roleRepository.save(role);
        
        log.info("Created role: {} with ID: {}", savedRole.getCode(), savedRole.getId());
        return savedRole;
    }

    /**
     * Find role by ID within current tenant.
     */
    public Optional<Role> findById(UUID roleId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return roleRepository.findById(roleId)
            .filter(role -> tenantId.equals(role.getTenantId()) && !role.isDeleted());
    }

    /**
     * Find role by code within current tenant.
     */
    public Optional<Role> findByCode(String code) {
        String tenantId = TenantContext.getCurrentTenantId();
        return roleRepository.findByCodeAndTenantId(code, tenantId)
            .filter(role -> !role.isDeleted());
    }

    /**
     * Get all roles for current tenant.
     */
    public List<Role> findAllRoles() {
        String tenantId = TenantContext.getCurrentTenantId();
        return roleRepository.findByTenantIdAndDeletedFalse(tenantId);
    }

    /**
     * Get system roles.
     */
    public List<Role> findSystemRoles() {
        String tenantId = TenantContext.getCurrentTenantId();
        return roleRepository.findByTenantIdAndSystemRoleTrue(tenantId);
    }

    /**
     * Add permission to role.
     */
    @Transactional
    public void addPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = findById(roleId)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        Permission permission = permissionRepository.findById(permissionId)
            .orElseThrow(() -> new PermissionNotFoundException("Permission not found: " + permissionId));
        
        role.addPermission(permission);
        roleRepository.save(role);
        
        log.info("Added permission {} to role {}", permission.getPermissionString(), role.getCode());
    }

    /**
     * Remove permission from role.
     */
    @Transactional
    public void removePermissionFromRole(UUID roleId, UUID permissionId) {
        Role role = findById(roleId)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        Permission permission = permissionRepository.findById(permissionId)
            .orElseThrow(() -> new PermissionNotFoundException("Permission not found: " + permissionId));
        
        role.removePermission(permission);
        roleRepository.save(role);
        
        log.info("Removed permission {} from role {}", permission.getPermissionString(), role.getCode());
    }

    /**
     * Assign role to user.
     */
    @Transactional
    public void assignRoleToUser(UUID userId, String roleCode) {
        User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        Role role = findByCode(roleCode)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleCode));
        
        user.getRoles().add(role);
        userService.updateUser(userId, user);
        
        log.info("Assigned role {} to user {}", roleCode, user.getUsername());
    }

    /**
     * Remove role from user.
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, String roleCode) {
        User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        Role role = findByCode(roleCode)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleCode));
        
        user.getRoles().remove(role);
        userService.updateUser(userId, user);
        
        log.info("Removed role {} from user {}", roleCode, user.getUsername());
    }

    /**
     * Check if user has permission.
     */
    public boolean hasPermission(UUID userId, String resource, String action) {
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        
        return user.hasPermission(resource, action);
    }

    /**
     * Check if user has role.
     */
    public boolean hasRole(UUID userId, String roleCode) {
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        
        return user.hasRole(roleCode);
    }

    /**
     * Get user roles.
     */
    public Set<Role> getUserRoles(UUID userId) {
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return Set.of();
        }
        
        return user.getRoles();
    }

    /**
     * Initialize default roles and permissions for a tenant.
     */
    @Transactional
    public void initializeDefaultRolesAndPermissions() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Initializing default roles and permissions for tenant: {}", tenantId);
        
        // Create default permissions
        createDefaultPermissions();
        
        // Create default roles
        createDefaultRoles();
        
        log.info("Initialized default roles and permissions for tenant: {}", tenantId);
    }

    private void createDefaultPermissions() {
        // Station management permissions
        createPermissionIfNotExists("STATION", "CREATE", "Create charging stations");
        createPermissionIfNotExists("STATION", "READ", "View charging stations");
        createPermissionIfNotExists("STATION", "UPDATE", "Update charging stations");
        createPermissionIfNotExists("STATION", "DELETE", "Delete charging stations");
        
        // User management permissions
        createPermissionIfNotExists("USER", "CREATE", "Create users");
        createPermissionIfNotExists("USER", "READ", "View users");
        createPermissionIfNotExists("USER", "UPDATE", "Update users");
        createPermissionIfNotExists("USER", "DELETE", "Delete users");
        createPermissionIfNotExists("USER", "ADMIN", "Admin user operations");
        
        // Token management permissions
        createPermissionIfNotExists("TOKEN", "CREATE", "Create auth tokens");
        createPermissionIfNotExists("TOKEN", "READ", "View auth tokens");
        createPermissionIfNotExists("TOKEN", "UPDATE", "Update auth tokens");
        createPermissionIfNotExists("TOKEN", "DELETE", "Delete auth tokens");
        createPermissionIfNotExists("TOKEN", "VERIFY", "Verify auth tokens");
        createPermissionIfNotExists("TOKEN", "ADMIN", "Admin token operations");
        
        // Session management permissions
        createPermissionIfNotExists("SESSION", "CREATE", "Start charging sessions");
        createPermissionIfNotExists("SESSION", "READ", "View charging sessions");
        createPermissionIfNotExists("SESSION", "UPDATE", "Update charging sessions");
        createPermissionIfNotExists("SESSION", "DELETE", "Delete charging sessions");
        
        // System permissions
        createPermissionIfNotExists("SYSTEM", "ADMIN", "System administration");
        createPermissionIfNotExists("SYSTEM", "READ", "View system information");
        createPermissionIfNotExists("SYSTEM", "MONITOR", "Monitor system health");
    }

    private void createDefaultRoles() {
        // Super Admin role (all permissions)
        Role adminRole = createRoleIfNotExists("ADMIN", "Administrator", 
            "Full system administrator with all permissions", true);
        addAllPermissionsToRole(adminRole);
        
        // Operator role (station and session management)
        Role operatorRole = createRoleIfNotExists("OPERATOR", "Operator", 
            "Charging station operator", false);
        addPermissionsToRole(operatorRole, List.of(
            "STATION:READ", "STATION:UPDATE",
            "SESSION:CREATE", "SESSION:READ", "SESSION:UPDATE",
            "USER:READ"
        ));
        
        // User role (basic user functionality)
        Role userRole = createRoleIfNotExists("USER", "User", 
            "Basic user role", false);
        addPermissionsToRole(userRole, List.of(
            "SESSION:CREATE", "SESSION:READ",
            "TOKEN:CREATE", "TOKEN:READ", "TOKEN:UPDATE"
        ));
        
        // Viewer role (read-only access)
        Role viewerRole = createRoleIfNotExists("VIEWER", "Viewer", 
            "Read-only access to system", false);
        addPermissionsToRole(viewerRole, List.of(
            "STATION:READ", "SESSION:READ", "USER:READ"
        ));
    }

    private void createPermissionIfNotExists(String resource, String action, String description) {
        if (!permissionRepository.existsByResourceAndAction(resource, action)) {
            Permission permission = Permission.builder()
                .resource(resource)
                .action(action)
                .name(resource + " " + action)
                .description(description)
                .build();
            
            permissionRepository.save(permission);
            log.debug("Created permission: {}:{}", resource, action);
        }
    }

    private Role createRoleIfNotExists(String code, String name, String description, boolean systemRole) {
        Optional<Role> existingRole = findByCode(code);
        if (existingRole.isPresent()) {
            return existingRole.get();
        }
        
        return createRole(code, name, description, systemRole);
    }

    private void addAllPermissionsToRole(Role role) {
        List<Permission> allPermissions = permissionRepository.findAll();
        for (Permission permission : allPermissions) {
            role.addPermission(permission);
        }
        roleRepository.save(role);
    }

    private void addPermissionsToRole(Role role, List<String> permissionStrings) {
        for (String permissionString : permissionStrings) {
            String[] parts = permissionString.split(":");
            if (parts.length == 2) {
                Optional<Permission> permission = permissionRepository
                    .findByResourceAndAction(parts[0], parts[1]);
                permission.ifPresent(role::addPermission);
            }
        }
        roleRepository.save(role);
    }

    /**
     * Repository interfaces.
     */
    @Repository
    public interface RoleRepository extends JpaRepository<Role, UUID> {
        Optional<Role> findByCodeAndTenantId(String code, String tenantId);
        boolean existsByCodeAndTenantId(String code, String tenantId);
        List<Role> findByTenantIdAndDeletedFalse(String tenantId);
        List<Role> findByTenantIdAndSystemRoleTrue(String tenantId);
    }

    @Repository
    public interface PermissionRepository extends JpaRepository<Permission, UUID> {
        Optional<Permission> findByResourceAndAction(String resource, String action);
        boolean existsByResourceAndAction(String resource, String action);
        
        @Query("SELECT p FROM Permission p WHERE p.resource = :resource")
        List<Permission> findByResource(@Param("resource") String resource);
    }
}