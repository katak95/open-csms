package com.opencsms.domain.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tenant billing information embedded in Tenant entity.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBilling {

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_plan", length = 50)
    private BillingPlan billingPlan = BillingPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 50)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "billing_address_line1", length = 255)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 255)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state_province", length = 100)
    private String billingStateProvince;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    @Column(name = "billing_country", length = 2)
    private String billingCountry;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "monthly_rate", precision = 10, scale = 2)
    private BigDecimal monthlyRate;

    @Column(name = "per_session_rate", precision = 10, scale = 4)
    private BigDecimal perSessionRate;

    @Column(name = "per_kwh_rate", precision = 10, scale = 4)
    private BigDecimal perKwhRate;

    @Column(name = "credit_balance", precision = 10, scale = 2)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    @Column(name = "credit_limit", precision = 10, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "invoice_prefix", length = 20)
    private String invoicePrefix;

    @Column(name = "last_invoice_number")
    private Long lastInvoiceNumber = 0L;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "purchase_order_number", length = 100)
    private String purchaseOrderNumber;

    public enum BillingPlan {
        FREE,
        STARTER,
        PROFESSIONAL,
        ENTERPRISE,
        CUSTOM
    }

    public enum BillingCycle {
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUAL,
        ANNUAL,
        PAY_AS_YOU_GO
    }
}