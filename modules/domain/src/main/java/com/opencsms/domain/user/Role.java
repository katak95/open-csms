package com.opencsms.domain.user;

import com.opencsms.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Role entity for role-based access control.
 */
@Entity
@Table(name = "roles",
    indexes = {
        @Index(name = "idx_roles_tenant", columnList = "tenant_id"),
        @Index(name = "idx_roles_code", columnList = "code"),
        @Index(name = "idx_roles_system", columnList = "system_role")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_roles_code_tenant", columnNames = {"code", "tenant_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "system_role")
    private boolean systemRole = false;

    // Relationships
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();

    // Helper methods
    public void addPermission(Permission permission) {
        permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
    }

    public boolean hasPermission(String resource, String action) {
        return permissions.stream()
            .anyMatch(permission -> 
                permission.getResource().equals(resource) && 
                permission.getAction().equals(action)
            );
    }
}