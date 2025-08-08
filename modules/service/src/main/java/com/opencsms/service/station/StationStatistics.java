package com.opencsms.service.station;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Station statistics data class.
 */
@Data
@Builder
public class StationStatistics {
    
    private long totalStations;
    private long onlineStations;
    private long availableStations;
    private long occupiedStations;
    private long faultedStations;
    
    private BigDecimal totalEnergyDelivered;
    private Long totalSessions;
    private BigDecimal totalRevenue;
    
    public long getOfflineStations() {
        return totalStations - onlineStations;
    }
    
    public double getOnlinePercentage() {
        return totalStations > 0 ? (double) onlineStations / totalStations * 100.0 : 0.0;
    }
    
    public double getUtilizationPercentage() {
        return totalStations > 0 ? (double) occupiedStations / totalStations * 100.0 : 0.0;
    }
    
    public BigDecimal getAverageRevenuePerStation() {
        if (totalStations > 0 && totalRevenue != null) {
            return totalRevenue.divide(BigDecimal.valueOf(totalStations), 2, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ZERO;
    }
    
    public BigDecimal getAverageEnergyPerStation() {
        if (totalStations > 0 && totalEnergyDelivered != null) {
            return totalEnergyDelivered.divide(BigDecimal.valueOf(totalStations), 3, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}