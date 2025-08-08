package com.opencsms.service.user;

import com.opencsms.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Service for validating user data and business rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {

    // Email validation regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    
    // Username validation regex (alphanumeric, dots, hyphens, underscores)
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._-]{3,50}$"
    );
    
    // Password strength regex (at least 8 chars, 1 letter, 1 number)
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$"
    );

    /**
     * Validate user creation data.
     */
    public void validateUserCreation(User user) {
        log.debug("Validating user creation: {}", user.getUsername());
        
        validateRequiredFields(user);
        validateUsername(user.getUsername());
        validateEmail(user.getEmail());
        validateOptionalFields(user);
    }

    /**
     * Validate user update data.
     */
    public void validateUserUpdate(User existingUser, User updates) {
        log.debug("Validating user update: {}", existingUser.getUsername());
        
        if (updates.getUsername() != null) {
            validateUsername(updates.getUsername());
        }
        
        if (updates.getEmail() != null) {
            validateEmail(updates.getEmail());
        }
        
        validateOptionalFields(updates);
    }

    /**
     * Validate password strength.
     */
    public void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new UserValidationException("Password cannot be empty");
        }
        
        if (password.length() < 8) {
            throw new UserValidationException("Password must be at least 8 characters long");
        }
        
        if (password.length() > 100) {
            throw new UserValidationException("Password must be less than 100 characters");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new UserValidationException(
                "Password must contain at least one letter and one number"
            );
        }
        
        // Check for common weak passwords
        if (isCommonPassword(password)) {
            throw new UserValidationException("Password is too common, please choose a stronger password");
        }
    }

    private void validateRequiredFields(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new UserValidationException("Username is required");
        }
        
        if (!StringUtils.hasText(user.getEmail())) {
            throw new UserValidationException("Email is required");
        }
    }

    private void validateUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new UserValidationException("Username cannot be empty");
        }
        
        if (username.length() < 3) {
            throw new UserValidationException("Username must be at least 3 characters long");
        }
        
        if (username.length() > 50) {
            throw new UserValidationException("Username must be less than 50 characters");
        }
        
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new UserValidationException(
                "Username can only contain letters, numbers, dots, hyphens, and underscores"
            );
        }
        
        // Check for reserved usernames
        if (isReservedUsername(username)) {
            throw new UserValidationException("Username is reserved and cannot be used");
        }
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new UserValidationException("Email cannot be empty");
        }
        
        if (email.length() > 255) {
            throw new UserValidationException("Email must be less than 255 characters");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new UserValidationException("Email format is invalid");
        }
    }

    private void validateOptionalFields(User user) {
        if (user.getFirstName() != null && user.getFirstName().length() > 100) {
            throw new UserValidationException("First name must be less than 100 characters");
        }
        
        if (user.getLastName() != null && user.getLastName().length() > 100) {
            throw new UserValidationException("Last name must be less than 100 characters");
        }
        
        if (user.getDisplayName() != null && user.getDisplayName().length() > 200) {
            throw new UserValidationException("Display name must be less than 200 characters");
        }
        
        if (user.getPhone() != null) {
            validatePhoneNumber(user.getPhone());
        }
        
        if (user.getMobile() != null) {
            validatePhoneNumber(user.getMobile());
        }
        
        if (user.getLanguage() != null) {
            validateLanguageCode(user.getLanguage());
        }
        
        if (user.getTimezone() != null) {
            validateTimezone(user.getTimezone());
        }
    }

    private void validatePhoneNumber(String phone) {
        if (phone.length() > 50) {
            throw new UserValidationException("Phone number must be less than 50 characters");
        }
        
        // Basic phone number validation (digits, spaces, hyphens, parentheses, plus)
        if (!phone.matches("^[\\d\\s\\-\\(\\)\\+]+$")) {
            throw new UserValidationException("Phone number contains invalid characters");
        }
    }

    private void validateLanguageCode(String language) {
        if (language.length() != 2 && language.length() != 5) {
            throw new UserValidationException("Language code must be 2 or 5 characters (e.g., 'en' or 'en-US')");
        }
        
        // Basic language code format validation
        if (!language.matches("^[a-z]{2}(-[A-Z]{2})?$")) {
            throw new UserValidationException("Invalid language code format");
        }
    }

    private void validateTimezone(String timezone) {
        if (timezone.length() > 100) {
            throw new UserValidationException("Timezone must be less than 100 characters");
        }
        
        // Could add more sophisticated timezone validation using ZoneId.of()
        // For now, just check it's not empty
        if (!StringUtils.hasText(timezone)) {
            throw new UserValidationException("Timezone cannot be empty");
        }
    }

    private boolean isReservedUsername(String username) {
        String lowerUsername = username.toLowerCase();
        return lowerUsername.equals("admin") ||
               lowerUsername.equals("root") ||
               lowerUsername.equals("system") ||
               lowerUsername.equals("support") ||
               lowerUsername.equals("api") ||
               lowerUsername.equals("service") ||
               lowerUsername.equals("daemon") ||
               lowerUsername.equals("guest") ||
               lowerUsername.equals("anonymous") ||
               lowerUsername.equals("user") ||
               lowerUsername.equals("test") ||
               lowerUsername.equals("demo") ||
               lowerUsername.startsWith("admin") ||
               lowerUsername.startsWith("system");
    }

    private boolean isCommonPassword(String password) {
        String lowerPassword = password.toLowerCase();
        
        // List of common weak passwords
        String[] commonPasswords = {
            "password", "password123", "123456", "123456789", "qwerty",
            "abc123", "password1", "admin", "letmein", "welcome",
            "monkey", "1234567890", "password12", "123123", "admin123"
        };
        
        for (String common : commonPasswords) {
            if (lowerPassword.equals(common)) {
                return true;
            }
        }
        
        // Check if password is just the username
        if (password.length() >= 8 && lowerPassword.contains("password")) {
            return true;
        }
        
        return false;
    }
}