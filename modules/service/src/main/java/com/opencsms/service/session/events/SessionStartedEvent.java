package com.opencsms.service.session.events;

import com.opencsms.domain.session.ChargingSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a charging session is started.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionStartedEvent {

    private ChargingSession session;
    private Instant timestamp = Instant.now();

    public SessionStartedEvent(ChargingSession session) {
        this.session = session;
        this.timestamp = Instant.now();
    }
}