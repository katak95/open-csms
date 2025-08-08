package com.opencsms.service.session;

import com.opencsms.domain.session.ChargingSession;
import com.opencsms.domain.tariff.Tariff;
import com.opencsms.domain.tariff.TariffElement;
import com.opencsms.domain.tariff.TariffRepository;
import com.opencsms.service.core.TenantContext;
import com.opencsms.service.session.exception.TariffNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing tariffs and calculating pricing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TariffService {

    private final TariffRepository tariffRepository;

    /**
     * Get default tariff for tenant
     */
    @Transactional(readOnly = true)
    public Tariff getDefaultTariff() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        Optional<Tariff> defaultTariff = tariffRepository.findByTenantIdAndDefaultTariffTrueAndActiveTrue(tenantId);
        if (defaultTariff.isPresent()) {
            return defaultTariff.get();
        }

        // Fallback to first active tariff
        List<Tariff> activeTariffs = tariffRepository.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId);
        if (!activeTariffs.isEmpty()) {
            return activeTariffs.get(0);
        }

        // Create default tariff if none exists
        return createDefaultTariff();
    }

    /**
     * Get tariff by ID
     */
    @Transactional(readOnly = true)
    public Tariff getTariffById(UUID tariffId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return tariffRepository.findById(tariffId)
            .filter(tariff -> tenantId.equals(tariff.getTenantId()))
            .orElseThrow(() -> new TariffNotFoundException("Tariff not found: " + tariffId));
    }

    /**
     * Get all active tariffs
     */
    @Transactional(readOnly = true)
    public List<Tariff> getActiveTariffs() {
        String tenantId = TenantContext.getCurrentTenantId();
        return tariffRepository.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Get currently valid tariffs
     */
    @Transactional(readOnly = true)
    public List<Tariff> getCurrentlyValidTariffs() {
        String tenantId = TenantContext.getCurrentTenantId();
        return tariffRepository.findCurrentlyValidTariffs(tenantId, Instant.now());
    }

    /**
     * Calculate session cost using tariff
     */
    public BigDecimal calculateSessionCost(ChargingSession session) {
        if (session.getTariffId() == null) {
            return calculateBasicCost(session);
        }

        try {
            Tariff tariff = getTariffById(session.getTariffId());
            return calculateCostWithTariff(session, tariff);
        } catch (Exception e) {
            log.warn("Error calculating cost with tariff for session {}: {}", session.getId(), e.getMessage());
            return calculateBasicCost(session);
        }
    }

    /**
     * Calculate cost using specific tariff
     */
    private BigDecimal calculateCostWithTariff(ChargingSession session, Tariff tariff) {
        BigDecimal totalCost = BigDecimal.ZERO;

        // Connection/service fee
        if (tariff.getConnectionFee() != null) {
            totalCost = totalCost.add(tariff.getConnectionFee());
        }
        if (tariff.getServiceFee() != null) {
            totalCost = totalCost.add(tariff.getServiceFee());
        }

        // Energy cost
        if (session.getEnergyDeliveredKwh() != null && session.getEnergyDeliveredKwh().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal energyPrice = getEnergyPrice(tariff, session);
            BigDecimal energyCost = session.getEnergyDeliveredKwh().multiply(energyPrice);
            totalCost = totalCost.add(energyCost);
        }

        // Time cost
        if (session.getDurationMinutes() != null && session.getDurationMinutes() > 0) {
            BigDecimal timePrice = getTimePrice(tariff, session);
            if (timePrice != null) {
                BigDecimal timeCost;
                if (tariff.getPricePerHour() != null) {
                    // Price per hour
                    timeCost = timePrice.multiply(BigDecimal.valueOf(session.getDurationMinutes()))
                                      .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                } else {
                    // Price per minute
                    timeCost = timePrice.multiply(BigDecimal.valueOf(session.getDurationMinutes()));
                }
                totalCost = totalCost.add(timeCost);
            }
        }

        // Apply billing increments
        totalCost = applyBillingIncrements(totalCost, tariff);

        // Apply tax if not included
        if (tariff.getTaxRate() != null && !Boolean.TRUE.equals(tariff.getTaxIncluded())) {
            BigDecimal tax = totalCost.multiply(tariff.getTaxRate()).setScale(2, RoundingMode.HALF_UP);
            totalCost = totalCost.add(tax);
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getEnergyPrice(Tariff tariff, ChargingSession session) {
        // Check for power-based pricing
        if (session.getMaxPowerKw() != null) {
            BigDecimal powerBasedPrice = tariff.getPricePerKwhForPower(session.getMaxPowerKw());
            if (powerBasedPrice != null) {
                return powerBasedPrice;
            }
        }

        // Use standard price
        return tariff.getPricePerKwh() != null ? tariff.getPricePerKwh() : BigDecimal.ZERO;
    }

    private BigDecimal getTimePrice(Tariff tariff, ChargingSession session) {
        if (tariff.getPricePerHour() != null) {
            return tariff.getPricePerHour();
        }
        if (tariff.getPricePerMinute() != null) {
            return tariff.getPricePerMinute();
        }
        return null;
    }

    private BigDecimal applyBillingIncrements(BigDecimal cost, Tariff tariff) {
        if (tariff.getBillingIncrementKwh() == null || cost.compareTo(BigDecimal.ZERO) == 0) {
            return cost;
        }

        // Round up to billing increment
        BigDecimal increment = tariff.getBillingIncrementKwh();
        return cost.divide(increment, 0, RoundingMode.UP).multiply(increment);
    }

    /**
     * Calculate basic cost without complex tariff rules
     */
    private BigDecimal calculateBasicCost(ChargingSession session) {
        BigDecimal energyPrice = session.getPricePerKwh() != null ? session.getPricePerKwh() : BigDecimal.valueOf(0.30);
        BigDecimal timePrice = session.getPricePerMinute() != null ? session.getPricePerMinute() : BigDecimal.valueOf(0.02);

        BigDecimal energyCost = BigDecimal.ZERO;
        BigDecimal timeCost = BigDecimal.ZERO;

        if (session.getEnergyDeliveredKwh() != null) {
            energyCost = session.getEnergyDeliveredKwh().multiply(energyPrice);
        }

        if (session.getDurationMinutes() != null) {
            timeCost = BigDecimal.valueOf(session.getDurationMinutes()).multiply(timePrice);
        }

        BigDecimal serviceFee = session.getServiceFee() != null ? session.getServiceFee() : BigDecimal.ZERO;

        return energyCost.add(timeCost).add(serviceFee).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Create tariff
     */
    public Tariff createTariff(Tariff tariff) {
        String tenantId = TenantContext.getCurrentTenantId();
        tariff.setTenantId(tenantId);
        
        validateTariff(tariff);
        
        return tariffRepository.save(tariff);
    }

    /**
     * Update tariff
     */
    public Tariff updateTariff(UUID tariffId, Tariff updatedTariff) {
        Tariff existingTariff = getTariffById(tariffId);
        
        // Update fields
        existingTariff.setName(updatedTariff.getName());
        existingTariff.setDescription(updatedTariff.getDescription());
        existingTariff.setPricePerKwh(updatedTariff.getPricePerKwh());
        existingTariff.setPricePerMinute(updatedTariff.getPricePerMinute());
        existingTariff.setServiceFee(updatedTariff.getServiceFee());
        existingTariff.setActive(updatedTariff.getActive());
        existingTariff.setVersion(existingTariff.getVersion() + 1);

        return tariffRepository.save(existingTariff);
    }

    /**
     * Delete tariff
     */
    public void deleteTariff(UUID tariffId) {
        Tariff tariff = getTariffById(tariffId);
        
        // Don't delete if it's the default tariff
        if (Boolean.TRUE.equals(tariff.getDefaultTariff())) {
            throw new IllegalStateException("Cannot delete default tariff");
        }
        
        tariffRepository.delete(tariff);
        log.info("Deleted tariff {} ({})", tariff.getName(), tariffId);
    }

    private void validateTariff(Tariff tariff) {
        if (tariff.getName() == null || tariff.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tariff name is required");
        }
        
        if (tariff.getCurrency() == null || tariff.getCurrency().length() != 3) {
            throw new IllegalArgumentException("Valid 3-letter currency code is required");
        }

        if (tariff.getPricePerKwh() == null && tariff.getPricePerMinute() == null) {
            throw new IllegalArgumentException("At least one price component is required");
        }
    }

    /**
     * Create default tariff for tenant
     */
    private Tariff createDefaultTariff() {
        String tenantId = TenantContext.getCurrentTenantId();
        
        Tariff defaultTariff = Tariff.builder()
            .tenantId(tenantId)
            .code("DEFAULT")
            .name("Default Tariff")
            .description("Default pricing for charging sessions")
            .tariffType(Tariff.TariffType.SIMPLE)
            .currency("EUR")
            .pricePerKwh(BigDecimal.valueOf(0.30))
            .pricePerMinute(BigDecimal.valueOf(0.02))
            .serviceFee(BigDecimal.ZERO)
            .active(true)
            .defaultTariff(true)
            .publicTariff(true)
            .build();

        Tariff savedTariff = tariffRepository.save(defaultTariff);
        log.info("Created default tariff for tenant {}", tenantId);
        
        return savedTariff;
    }
}