package com.opencsms.service.user;

import com.opencsms.domain.user.AuthToken;
import com.opencsms.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service for publishing user-related events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventService {

    private final ApplicationEventPublisher eventPublisher;

    public void recordUserCreated(User user) {
        log.debug("Recording user created event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserCreatedEvent(user));
    }

    public void recordUserUpdated(User user) {
        log.debug("Recording user updated event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserUpdatedEvent(user));
    }

    public void recordUserDeactivated(User user) {
        log.debug("Recording user deactivated event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserDeactivatedEvent(user));
    }

    public void recordUserReactivated(User user) {
        log.debug("Recording user reactivated event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserReactivatedEvent(user));
    }

    public void recordUserSuspended(User user, String reason) {
        log.debug("Recording user suspended event: {} - {}", user.getUsername(), reason);
        eventPublisher.publishEvent(new UserSuspendedEvent(user, reason));
    }

    public void recordUserUnsuspended(User user) {
        log.debug("Recording user unsuspended event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserUnsuspendedEvent(user));
    }

    public void recordUserLocked(User user, int minutes) {
        log.debug("Recording user locked event: {} for {} minutes", user.getUsername(), minutes);
        eventPublisher.publishEvent(new UserLockedEvent(user, minutes));
    }

    public void recordUserUnlocked(User user) {
        log.debug("Recording user unlocked event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserUnlockedEvent(user));
    }

    public void recordPasswordChanged(User user) {
        log.debug("Recording password changed event: {}", user.getUsername());
        eventPublisher.publishEvent(new PasswordChangedEvent(user));
    }

    public void recordUserLoggedIn(User user, String ipAddress) {
        log.debug("Recording user logged in event: {} from {}", user.getUsername(), ipAddress);
        eventPublisher.publishEvent(new UserLoggedInEvent(user, ipAddress));
    }

    public void recordUserLoginFailed(User user) {
        log.debug("Recording user login failed event: {}", user.getUsername());
        eventPublisher.publishEvent(new UserLoginFailedEvent(user));
    }

    // Token events
    public void recordTokenCreated(AuthToken token) {
        log.debug("Recording token created event: {}", token.getName());
        eventPublisher.publishEvent(new TokenCreatedEvent(token));
    }

    public void recordTokenUpdated(AuthToken token) {
        log.debug("Recording token updated event: {}", token.getName());
        eventPublisher.publishEvent(new TokenUpdatedEvent(token));
    }

    public void recordTokenActivated(AuthToken token) {
        log.debug("Recording token activated event: {}", token.getName());
        eventPublisher.publishEvent(new TokenActivatedEvent(token));
    }

    public void recordTokenDeactivated(AuthToken token) {
        log.debug("Recording token deactivated event: {}", token.getName());
        eventPublisher.publishEvent(new TokenDeactivatedEvent(token));
    }

    public void recordTokenBlocked(AuthToken token, String reason) {
        log.debug("Recording token blocked event: {} - {}", token.getName(), reason);
        eventPublisher.publishEvent(new TokenBlockedEvent(token, reason));
    }

    public void recordTokenUnblocked(AuthToken token) {
        log.debug("Recording token unblocked event: {}", token.getName());
        eventPublisher.publishEvent(new TokenUnblockedEvent(token));
    }

    public void recordTokenDeleted(AuthToken token) {
        log.debug("Recording token deleted event: {}", token.getName());
        eventPublisher.publishEvent(new TokenDeletedEvent(token));
    }

    public void recordTokenUsed(AuthToken token) {
        log.debug("Recording token used event: {}", token.getName());
        eventPublisher.publishEvent(new TokenUsedEvent(token));
    }

    // Event classes
    public record UserCreatedEvent(User user) {}
    public record UserUpdatedEvent(User user) {}
    public record UserDeactivatedEvent(User user) {}
    public record UserReactivatedEvent(User user) {}
    public record UserSuspendedEvent(User user, String reason) {}
    public record UserUnsuspendedEvent(User user) {}
    public record UserLockedEvent(User user, int minutes) {}
    public record UserUnlockedEvent(User user) {}
    public record PasswordChangedEvent(User user) {}
    public record UserLoggedInEvent(User user, String ipAddress) {}
    public record UserLoginFailedEvent(User user) {}
    
    // Token event classes
    public record TokenCreatedEvent(AuthToken token) {}
    public record TokenUpdatedEvent(AuthToken token) {}
    public record TokenActivatedEvent(AuthToken token) {}
    public record TokenDeactivatedEvent(AuthToken token) {}
    public record TokenBlockedEvent(AuthToken token, String reason) {}
    public record TokenUnblockedEvent(AuthToken token) {}
    public record TokenDeletedEvent(AuthToken token) {}
    public record TokenUsedEvent(AuthToken token) {}
}