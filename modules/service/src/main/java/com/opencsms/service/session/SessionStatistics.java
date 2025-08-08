package com.opencsms.service.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Session statistics data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatistics {

    private Long totalSessions;
    private BigDecimal totalEnergyDelivered;
    private Integer averageDurationMinutes;
    private BigDecimal totalRevenue;
    private Long activeSessions;
    private Long completedSessions;
    private Long failedSessions;
}