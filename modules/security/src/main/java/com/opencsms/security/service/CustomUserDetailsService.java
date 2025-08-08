package com.opencsms.security.service;

import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.user.User;
import com.opencsms.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService implementation for multi-tenant authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String tenantId = TenantContext.getCurrentTenantOrNull();
        
        if (tenantId == null) {
            log.warn("No tenant context when loading user: {}", username);
            throw new UsernameNotFoundException("Tenant context required");
        }
        
        User user = userRepository.findByUsernameAndTenantId(username, tenantId)
            .orElseThrow(() -> new UsernameNotFoundException(
                String.format("User not found: %s in tenant: %s", username, tenantId)
            ));
        
        if (!user.isActive()) {
            throw new UsernameNotFoundException("User account is not active");
        }
        
        if (user.isDeleted()) {
            throw new UsernameNotFoundException("User account has been deleted");
        }
        
        return new CustomUserDetails(user);
    }
    
    /**
     * Custom UserDetails implementation.
     */
    public static class CustomUserDetails implements UserDetails {
        
        private final User user;
        private final Collection<? extends GrantedAuthority> authorities;
        
        public CustomUserDetails(User user) {
            this.user = user;
            this.authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(
                    permission.getResource() + ":" + permission.getAction()
                ))
                .collect(Collectors.toSet());
        }
        
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }
        
        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }
        
        @Override
        public String getUsername() {
            return user.getUsername();
        }
        
        @Override
        public boolean isAccountNonExpired() {
            return true;
        }
        
        @Override
        public boolean isAccountNonLocked() {
            return user.getLockedUntil() == null || 
                   user.getLockedUntil().isBefore(java.time.Instant.now());
        }
        
        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }
        
        @Override
        public boolean isEnabled() {
            return user.isActive() && !user.isSuspended();
        }
        
        public User getUser() {
            return user;
        }
        
        public String getTenantId() {
            return user.getTenantId();
        }
        
        public String getUserId() {
            return user.getId().toString();
        }
    }
}