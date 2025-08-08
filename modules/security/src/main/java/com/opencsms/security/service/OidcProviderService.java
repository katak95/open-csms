package com.opencsms.security.service;

import com.opencsms.domain.user.User;
import com.opencsms.security.jwt.JwtService;
import com.opencsms.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OpenID Connect (OIDC) provider service for third-party authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OidcProviderService {

    private final UserService userService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;

    @Value("${app.oidc.google.client-id:}")
    private String googleClientId;
    
    @Value("${app.oidc.google.client-secret:}")
    private String googleClientSecret;
    
    @Value("${app.oidc.google.redirect-uri:}")
    private String googleRedirectUri;
    
    @Value("${app.oidc.microsoft.client-id:}")
    private String microsoftClientId;
    
    @Value("${app.oidc.microsoft.client-secret:}")
    private String microsoftClientSecret;
    
    @Value("${app.oidc.microsoft.redirect-uri:}")
    private String microsoftRedirectUri;

    /**
     * Generate OIDC authorization URL for Google.
     */
    public String generateGoogleAuthUrl(String state) {
        log.debug("Generating Google OIDC authorization URL");
        
        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", googleClientId)
            .queryParam("redirect_uri", googleRedirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .toUriString();
    }

    /**
     * Generate OIDC authorization URL for Microsoft.
     */
    public String generateMicrosoftAuthUrl(String state) {
        log.debug("Generating Microsoft OIDC authorization URL");
        
        return UriComponentsBuilder.fromHttpUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
            .queryParam("client_id", microsoftClientId)
            .queryParam("redirect_uri", microsoftRedirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .toUriString();
    }

    /**
     * Handle Google OIDC callback and authenticate user.
     */
    public MultiProviderAuthenticationService.AuthenticationResult handleGoogleCallback(
            String authCode, String state, String ipAddress) {
        
        log.debug("Handling Google OIDC callback");
        
        try {
            // Exchange authorization code for tokens
            GoogleTokenResponse tokenResponse = exchangeGoogleAuthCode(authCode);
            
            // Get user info from Google
            GoogleUserInfo userInfo = getGoogleUserInfo(tokenResponse.getAccessToken());
            
            // Find or create user
            User user = findOrCreateOidcUser(userInfo.getEmail(), userInfo.getName(), 
                                           userInfo.getGivenName(), userInfo.getFamilyName());
            
            // Record successful login
            userService.recordLoginSuccess(user.getId(), ipAddress);
            
            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getUsername());
            
            log.info("User authenticated with Google OIDC: {} from IP: {}", 
                    user.getUsername(), ipAddress);
            
            return MultiProviderAuthenticationService.AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(MultiProviderAuthenticationService.AuthenticationMethod.OIDC)
                .message("Google OIDC authentication successful")
                .build();
                
        } catch (Exception e) {
            log.error("Google OIDC authentication failed: {}", e.getMessage(), e);
            
            return MultiProviderAuthenticationService.AuthenticationResult.builder()
                .success(false)
                .authMethod(MultiProviderAuthenticationService.AuthenticationMethod.OIDC)
                .message("Google OIDC authentication failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Handle Microsoft OIDC callback and authenticate user.
     */
    public MultiProviderAuthenticationService.AuthenticationResult handleMicrosoftCallback(
            String authCode, String state, String ipAddress) {
        
        log.debug("Handling Microsoft OIDC callback");
        
        try {
            // Exchange authorization code for tokens
            MicrosoftTokenResponse tokenResponse = exchangeMicrosoftAuthCode(authCode);
            
            // Get user info from Microsoft
            MicrosoftUserInfo userInfo = getMicrosoftUserInfo(tokenResponse.getAccessToken());
            
            // Find or create user
            User user = findOrCreateOidcUser(userInfo.getMail() != null ? userInfo.getMail() : userInfo.getUserPrincipalName(), 
                                           userInfo.getDisplayName(), 
                                           userInfo.getGivenName(), 
                                           userInfo.getSurname());
            
            // Record successful login
            userService.recordLoginSuccess(user.getId(), ipAddress);
            
            // Generate JWT token
            String jwtToken = jwtService.generateToken(user.getUsername());
            
            log.info("User authenticated with Microsoft OIDC: {} from IP: {}", 
                    user.getUsername(), ipAddress);
            
            return MultiProviderAuthenticationService.AuthenticationResult.builder()
                .success(true)
                .user(user)
                .authToken(jwtToken)
                .authMethod(MultiProviderAuthenticationService.AuthenticationMethod.OIDC)
                .message("Microsoft OIDC authentication successful")
                .build();
                
        } catch (Exception e) {
            log.error("Microsoft OIDC authentication failed: {}", e.getMessage(), e);
            
            return MultiProviderAuthenticationService.AuthenticationResult.builder()
                .success(false)
                .authMethod(MultiProviderAuthenticationService.AuthenticationMethod.OIDC)
                .message("Microsoft OIDC authentication failed: " + e.getMessage())
                .build();
        }
    }

    private GoogleTokenResponse exchangeGoogleAuthCode(String authCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", googleClientId);
        body.add("client_secret", googleClientSecret);
        body.add("code", authCode);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", googleRedirectUri);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<GoogleTokenResponse> response = restTemplate.exchange(
            "https://oauth2.googleapis.com/token",
            HttpMethod.POST,
            request,
            GoogleTokenResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BadCredentialsException("Failed to exchange Google auth code for tokens");
        }
        
        return response.getBody();
    }

    private GoogleUserInfo getGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
            "https://www.googleapis.com/oauth2/v2/userinfo",
            HttpMethod.GET,
            request,
            GoogleUserInfo.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BadCredentialsException("Failed to get Google user info");
        }
        
        return response.getBody();
    }

    private MicrosoftTokenResponse exchangeMicrosoftAuthCode(String authCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", microsoftClientId);
        body.add("client_secret", microsoftClientSecret);
        body.add("code", authCode);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", microsoftRedirectUri);
        body.add("scope", "openid email profile");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<MicrosoftTokenResponse> response = restTemplate.exchange(
            "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            HttpMethod.POST,
            request,
            MicrosoftTokenResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BadCredentialsException("Failed to exchange Microsoft auth code for tokens");
        }
        
        return response.getBody();
    }

    private MicrosoftUserInfo getMicrosoftUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        ResponseEntity<MicrosoftUserInfo> response = restTemplate.exchange(
            "https://graph.microsoft.com/v1.0/me",
            HttpMethod.GET,
            request,
            MicrosoftUserInfo.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BadCredentialsException("Failed to get Microsoft user info");
        }
        
        return response.getBody();
    }

    private User findOrCreateOidcUser(String email, String displayName, 
                                    String firstName, String lastName) {
        // Try to find existing user by email
        Optional<User> existingUser = userService.findByEmail(email);
        
        if (existingUser.isPresent()) {
            log.debug("Found existing user for OIDC email: {}", email);
            return existingUser.get();
        }
        
        // Create new user
        log.info("Creating new user from OIDC authentication: {}", email);
        
        String username = generateUniqueUsername(email);
        
        User newUser = User.builder()
            .username(username)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .displayName(displayName)
            .emailVerified(true) // OIDC providers verify emails
            .active(true)
            .language("en")
            .timezone("UTC")
            .notificationsEnabled(true)
            .build();
        
        // Add metadata about OIDC authentication
        newUser.getMetadata().put("oidc_created", Instant.now().toString());
        newUser.getMetadata().put("oidc_email", email);
        
        return userService.createUser(newUser);
    }

    private String generateUniqueUsername(String email) {
        // Extract username part from email
        String baseUsername = email.split("@")[0];
        
        // Clean up username (remove non-alphanumeric characters)
        baseUsername = baseUsername.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        // Ensure minimum length
        if (baseUsername.length() < 3) {
            baseUsername = "user" + baseUsername;
        }
        
        String username = baseUsername;
        int counter = 1;
        
        // Find unique username
        while (userService.findByUsername(username).isPresent()) {
            username = baseUsername + counter;
            counter++;
        }
        
        return username;
    }

    // Google response DTOs
    @lombok.Data
    private static class GoogleTokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private String idToken;
    }

    @lombok.Data
    private static class GoogleUserInfo {
        private String id;
        private String email;
        private Boolean verifiedEmail;
        private String name;
        private String givenName;
        private String familyName;
        private String picture;
        private String locale;
    }

    // Microsoft response DTOs
    @lombok.Data
    private static class MicrosoftTokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private String idToken;
        private String scope;
    }

    @lombok.Data
    private static class MicrosoftUserInfo {
        private String id;
        private String displayName;
        private String givenName;
        private String surname;
        private String mail;
        private String userPrincipalName;
        private String jobTitle;
        private String mobilePhone;
        private String businessPhones;
    }
}