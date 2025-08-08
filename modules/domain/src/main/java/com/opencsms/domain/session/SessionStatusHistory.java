package com.opencsms.domain.session;

import com.opencsms.domain.core.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking charging session status transitions.
 */
@Entity
@Table(name = "session_status_history", indexes = {
    @Index(name = "idx_status_history_session", columnList = "session_id"),
    @Index(name = "idx_status_history_timestamp", columnList = "timestamp")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChargingSession chargingSession;

    @Column(name = "session_id", insertable = false, updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ChargingSession.SessionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ChargingSession.SessionStatus toStatus;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;
}