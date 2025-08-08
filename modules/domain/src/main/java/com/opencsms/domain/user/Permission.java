package com.opencsms.domain.user;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Permission entity for fine-grained access control.
 */
@Entity
@Table(name = "permissions",
    indexes = {
        @Index(name = "idx_permissions_resource", columnList = "resource"),
        @Index(name = "idx_permissions_action", columnList = "action")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_permissions_resource_action", columnNames = {"resource", "action"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Relationships
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    private Set<Role> roles = new HashSet<>();

    public String getPermissionString() {
        return resource + ":" + action;
    }

    @Override
    public String toString() {
        return String.format("Permission{resource='%s', action='%s', name='%s'}", 
                           resource, action, name);
    }
}