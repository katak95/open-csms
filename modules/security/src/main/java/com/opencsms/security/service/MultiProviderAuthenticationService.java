package com.opencsms.security.service;

import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import com.opencsms.security.jwt.JwtService;
import com.opencsms.service.user.AuthTokenService;
import com.opencsms.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Multi-provider authentication service supporting various authentication methods.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiProviderAuthenticationService {

    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Authenticate using username/email and password.
     */
    public AuthenticationResult authenticateWithPassword(String usernameOrEmail, 
                                                       String password, 
                                                       String ipAddress) {
        log.debug("Authenticating user with password: {}", usernameOrEmail);
        
        try {
            // Find the user
            Optional<User> userOpt = userService.findByUsername(usernameOrEmail);
            if (userOpt.isEmpty()) {
                userOpt = userService.findByEmail(usernameOrEmail);
            }
            
            if (userOpt.isEmpty()) {
                userService.recordLoginFailure(usernameOrEmail);
                throw new BadCredentialsException("Invalid credentials");
            }
            
            User user = userOpt.get();
            
            // Check user status
            validateUserStatus(user);
            
            // Authenticate with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), password)
            );
            
            if (!authentication.isAuthenticated()) {
                userService.recordLoginFailure(usernameOrEmail);
                throw new BadCredentialsException("Invalid credentials");
            }
            
            // Record successful login
            userService.recordLoginSuccess(user.getId(), ipAddress);
            
            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getUsername());
            
            log.info("User authenticated successfully: {} from IP: {}", user.getUsername(), ipAddress);
            
            return AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(AuthenticationMethod.PASSWORD)
                .message("Authentication successful")
                .build();
                
        } catch (AuthenticationException e) {
            userService.recordLoginFailure(usernameOrEmail);
            log.warn("Authentication failed for user: {} - {}", usernameOrEmail, e.getMessage());
            
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(AuthenticationMethod.PASSWORD)
                .message(e.getMessage())
                .build();
        }
    }

    /**
     * Authenticate using RFID token.
     */
    public AuthenticationResult authenticateWithRfid(String rfidValue, String ipAddress) {
        log.debug("Authenticating with RFID token: {}", rfidValue);
        
        // Find and verify RFID token
        if (!authTokenService.verifyAndUseToken(rfidValue)) {
            log.warn("RFID authentication failed - invalid token: {}", rfidValue);
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(AuthenticationMethod.RFID)
                .message("Invalid RFID token")
                .build();
        }
        
        // Find the token and user
        Optional<AuthToken> tokenOpt = authTokenService.findByTokenValue(rfidValue);
        if (tokenOpt.isEmpty() || tokenOpt.get().getUser() == null) {
            log.error("RFID token found but user not associated: {}", rfidValue);
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(AuthenticationMethod.RFID)
                .message("Token not properly configured")
                .build();
        }
        
        AuthToken authToken = tokenOpt.get();
        User user = authToken.getUser();
        
        try {
            // Check user status
            validateUserStatus(user);
            
            // Record successful login
            userService.recordLoginSuccess(user.getId(), ipAddress);
            
            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getUsername());
            
            log.info("User authenticated with RFID: {} from IP: {}", user.getUsername(), ipAddress);
            
            return AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(AuthenticationMethod.RFID)
                .message("RFID authentication successful")
                .build();
                
        } catch (AuthenticationException e) {
            log.warn("RFID authentication failed for user: {} - {}", user.getUsername(), e.getMessage());
            
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(AuthenticationMethod.RFID)
                .message(e.getMessage())
                .build();
        }
    }

    /**
     * Authenticate using NFC token.
     */
    public AuthenticationResult authenticateWithNfc(String nfcValue, String ipAddress) {
        log.debug("Authenticating with NFC token: {}", nfcValue);
        
        // NFC authentication follows the same pattern as RFID
        return authenticateWithToken(nfcValue, AuthenticationMethod.NFC, ipAddress);
    }

    /**
     * Authenticate using API key.
     */
    public AuthenticationResult authenticateWithApiKey(String apiKey, String ipAddress) {
        log.debug("Authenticating with API key: {}", apiKey);
        
        return authenticateWithToken(apiKey, AuthenticationMethod.API_KEY, ipAddress);
    }

    /**
     * Authenticate using mobile app token.
     */
    public AuthenticationResult authenticateWithMobileToken(String mobileToken, String ipAddress) {
        log.debug("Authenticating with mobile token: {}", mobileToken);
        
        return authenticateWithToken(mobileToken, AuthenticationMethod.MOBILE_APP, ipAddress);
    }

    /**
     * Refresh JWT token.
     */
    public AuthenticationResult refreshToken(String username) {
        log.debug("Refreshing JWT token for user: {}", username);
        
        try {
            // Verify user exists and is active
            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isEmpty()) {
                throw new BadCredentialsException("User not found");
            }
            
            User user = userOpt.get();
            validateUserStatus(user);
            
            // Generate new JWT token
            String jwtToken = jwtService.generateToken(username);
            
            return AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(AuthenticationMethod.JWT_REFRESH)
                .message("Token refreshed successfully")
                .build();
                
        } catch (AuthenticationException e) {
            log.warn("Token refresh failed for user: {} - {}", username, e.getMessage());
            
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(AuthenticationMethod.JWT_REFRESH)
                .message(e.getMessage())
                .build();
        }
    }

    /**
     * Validate JWT token and return user details.
     */
    public Optional<UserDetails> validateJwtToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            
            if (username != null && jwtService.isTokenValid(token, username)) {
                return Optional.of(userDetailsService.loadUserByUsername(username));
            }
            
        } catch (Exception e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
        }
        
        return Optional.empty();
    }

    private AuthenticationResult authenticateWithToken(String tokenValue, 
                                                     AuthenticationMethod method, 
                                                     String ipAddress) {
        // Find and verify token
        if (!authTokenService.verifyAndUseToken(tokenValue)) {
            log.warn("{} authentication failed - invalid token: {}", method, tokenValue);
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(method)
                .message("Invalid " + method.name().toLowerCase() + " token")
                .build();
        }
        
        // Find the token and user
        Optional<AuthToken> tokenOpt = authTokenService.findByTokenValue(tokenValue);
        if (tokenOpt.isEmpty() || tokenOpt.get().getUser() == null) {
            log.error("{} token found but user not associated: {}", method, tokenValue);
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(method)
                .message("Token not properly configured")
                .build();
        }
        
        AuthToken authToken = tokenOpt.get();
        User user = authToken.getUser();
        
        try {
            // Check user status
            validateUserStatus(user);
            
            // Record successful login
            userService.recordLoginSuccess(user.getId(), ipAddress);
            
            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getUsername());
            
            log.info("User authenticated with {}: {} from IP: {}", 
                    method, user.getUsername(), ipAddress);
            
            return AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(method)
                .message(method.name() + " authentication successful")
                .build();
                
        } catch (AuthenticationException e) {
            log.warn("{} authentication failed for user: {} - {}", 
                    method, user.getUsername(), e.getMessage());
            
            return AuthenticationResult.builder()
                .success(false)
                .authMethod(method)
                .message(e.getMessage())
                .build();
        }
    }

    private void validateUserStatus(User user) {
        if (!user.isActive()) {
            throw new DisabledException("User account is deactivated");
        }
        
        if (user.isSuspended()) {
            throw new DisabledException("User account is suspended: " + user.getSuspensionReason());
        }
        
        if (user.isLocked()) {
            throw new LockedException("User account is locked");
        }
        
        if (user.isDeleted()) {
            throw new DisabledException("User account not found");
        }
    }

    /**
     * Authentication method enumeration.
     */
    public enum AuthenticationMethod {
        PASSWORD,
        RFID,
        NFC,
        API_KEY,
        MOBILE_APP,
        OAUTH2,
        OIDC,
        SAML,
        JWT_REFRESH
    }

    /**
     * Authentication result data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthenticationResult {
        private boolean success;
        private User user;
        private String authToken;
        private AuthenticationMethod authMethod;
        private String message;
        private Instant timestamp = Instant.now();
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isFailure() {
            return !success;
        }
    }
}