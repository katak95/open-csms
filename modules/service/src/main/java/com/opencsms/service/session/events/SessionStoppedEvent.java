package com.opencsms.service.session.events;

import com.opencsms.domain.session.ChargingSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a charging session is stopped.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionStoppedEvent {

    private ChargingSession session;
    private Instant timestamp = Instant.now();

    public SessionStoppedEvent(ChargingSession session) {
        this.session = session;
        this.timestamp = Instant.now();
    }
}