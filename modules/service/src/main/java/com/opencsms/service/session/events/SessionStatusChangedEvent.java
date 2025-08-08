package com.opencsms.service.session.events;

import com.opencsms.domain.session.ChargingSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a charging session status changes.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionStatusChangedEvent {

    private ChargingSession session;
    private ChargingSession.SessionStatus fromStatus;
    private ChargingSession.SessionStatus toStatus;
    private Instant timestamp = Instant.now();

    public SessionStatusChangedEvent(ChargingSession session, 
                                   ChargingSession.SessionStatus fromStatus, 
                                   ChargingSession.SessionStatus toStatus) {
        this.session = session;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.timestamp = Instant.now();
    }
}