package com.haodaone.billing.service;

import com.haodaone.billing.gst.GstinValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Billing configuration. Every key has a safe default so the app starts
 * without new environment variables:
 *  - seller details are empty by default; invoices then print
 *    "GSTIN: not provided" and charge no GST.
 *  - SAC defaults to 997331 ("Licensing services for the right to use
 *    computer software and databases") - the code most Indian SaaS vendors
 *    use for subscription access to hosted software. 998314 (IT design and
 *    development) describes custom development work, not a subscription.
 *    Override with app.billing.seller-sac if your CA advises otherwise.
 */
@Component
public class BillingSettings {

    @Value("${app.billing.seller-name:}")
    private String sellerName;
    @Value("${app.billing.seller-gstin:}")
    private String sellerGstin;
    @Value("${app.billing.seller-address:}")
    private String sellerAddress;
    @Value("${app.billing.seller-state-code:}")
    private String sellerStateCode;
    @Value("${app.billing.seller-pan:}")
    private String sellerPan;
    @Value("${app.billing.seller-sac:997331}")
    private String sac;
    @Value("${app.billing.invoice-prefix:VTR}")
    private String invoicePrefix;
    @Value("${app.billing.grace-days:7}")
    private int graceDays = 7;
    /** 0 = no extra cap beyond the subscription's employee limit. */
    @Value("${app.billing.seat-hard-cap:0}")
    private int seatHardCap;
    @Value("${app.billing.razorpay-subscriptions-enabled:false}")
    private boolean razorpaySubscriptionsEnabled;
    @Value("${app.frontend-url:https://app.vettrihrms.in}")
    private String frontendUrl;

    public BillingSettings() {
    }

    /** Test constructor. */
    public BillingSettings(String sellerName, String sellerGstin, String sellerAddress, String sellerStateCode,
                           int graceDays, int seatHardCap) {
        this.sellerName = sellerName;
        this.sellerGstin = sellerGstin;
        this.sellerAddress = sellerAddress;
        this.sellerStateCode = sellerStateCode;
        this.sellerPan = "";
        this.sac = "997331";
        this.invoicePrefix = "VTR";
        this.graceDays = graceDays;
        this.seatHardCap = seatHardCap;
        this.frontendUrl = "https://app.vettrihrms.in";
    }

    public String sellerName() { return blank(sellerName) ? "Vettri HRMS" : sellerName.trim(); }
    public String sellerGstin() { return blank(sellerGstin) ? null : GstinValidator.normalize(sellerGstin); }
    public String sellerAddress() { return blank(sellerAddress) ? null : sellerAddress.trim(); }
    public String sellerPan() { return blank(sellerPan) ? null : sellerPan.trim(); }
    public String sac() { return blank(sac) ? "997331" : sac.trim(); }
    public String invoicePrefix() { return blank(invoicePrefix) ? "VTR" : invoicePrefix.trim(); }
    public int graceDays() { return Math.max(0, graceDays); }
    public int seatHardCap() { return Math.max(0, seatHardCap); }
    public boolean razorpaySubscriptionsEnabled() { return razorpaySubscriptionsEnabled; }

    public String sellerStateCode() {
        if (!blank(sellerStateCode)) return sellerStateCode.trim();
        return GstinValidator.stateCodeOf(sellerGstin());
    }

    public String billingUrl() {
        String base = blank(frontendUrl) ? "https://app.vettrihrms.in" : frontendUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/billing";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
