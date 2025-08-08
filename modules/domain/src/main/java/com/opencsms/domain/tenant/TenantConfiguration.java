package com.opencsms.domain.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant configuration settings embedded in Tenant entity.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfiguration {

    @Column(name = "timezone", length = 100)
    private String timezone = "UTC";

    @Column(name = "currency", length = 3)
    private String currency = "EUR";

    @Column(name = "language", length = 5)
    private String language = "en";

    @Column(name = "date_format", length = 50)
    private String dateFormat = "yyyy-MM-dd";

    @Column(name = "time_format", length = 50)
    private String timeFormat = "HH:mm:ss";

    @Column(name = "max_stations")
    private Integer maxStations;

    @Column(name = "max_connectors")
    private Integer maxConnectors;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_sessions_per_month")
    private Integer maxSessionsPerMonth;

    @Column(name = "max_energy_per_month_kwh")
    private Long maxEnergyPerMonthKwh;

    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes = 240;

    @Column(name = "idle_timeout_minutes")
    private Integer idleTimeoutMinutes = 30;

    @Column(name = "max_power_kw")
    private Integer maxPowerKw;

    @Column(name = "allow_remote_start")
    private boolean allowRemoteStart = true;

    @Column(name = "allow_remote_stop")
    private boolean allowRemoteStop = true;

    @Column(name = "require_authorization")
    private boolean requireAuthorization = true;

    @Column(name = "allow_guest_charging")
    private boolean allowGuestCharging = false;

    @Column(name = "auto_stop_on_disconnect")
    private boolean autoStopOnDisconnect = true;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "api_rate_limit_per_minute")
    private Integer apiRateLimitPerMinute = 600;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor = "#1976D2";

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor = "#DC004E";
}