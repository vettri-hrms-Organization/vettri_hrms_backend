package com.haodaone.billing.service;

import com.haodaone.common.exception.BadRequestException;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Thin wrapper over the Razorpay Java SDK (orders, payments, plans,
 * subscriptions) used by renewal billing; isolated so the billing rules can
 * be unit tested with a mock.
 */
@Component
public class RazorpayGateway {

    @Value("${app.razorpay.key-id:}")
    private String keyId;
    @Value("${app.razorpay.key-secret:}")
    private String keySecret;
    @Value("${app.razorpay.currency:INR}")
    private String currency;

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public String keyId() {
        return keyId;
    }

    public String currency() {
        return currency == null || currency.isBlank() ? "INR" : currency;
    }

    private RazorpayClient client() {
        if (!isConfigured()) {
            throw new BadRequestException("Payment is temporarily unavailable. Add Razorpay credentials to the backend environment.");
        }
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException ex) {
            throw new BadRequestException("Payment is temporarily unavailable.");
        }
    }

    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", currency());
            request.put("receipt", receipt);
            request.put("notes", new JSONObject(notes));
            Order order = client().orders.create(request);
            return order.get("id");
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to create a Razorpay order: " + ex.getMessage());
        }
    }

    public JSONObject fetchOrder(String orderId) {
        try {
            return client().orders.fetch(orderId).toJson();
        } catch (RazorpayException ex) {
            throw new BadRequestException("Razorpay order not found for verification.");
        }
    }

    public JSONObject fetchPayment(String paymentId) {
        try {
            Payment payment = client().payments.fetch(paymentId);
            return payment.toJson();
        } catch (RazorpayException ex) {
            throw new BadRequestException("Razorpay payment not found or inaccessible.");
        }
    }

    /** Checkout signature: HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (signature == null || signature.isBlank() || !isConfigured()) return false;
        return constantTimeEquals(hmac(orderId + "|" + paymentId, keySecret), signature);
    }

    /** Subscription checkout signature: HMAC_SHA256(payment_id + "|" + subscription_id, key_secret). */
    public boolean verifySubscriptionSignature(String subscriptionId, String paymentId, String signature) {
        if (signature == null || signature.isBlank() || !isConfigured()) return false;
        return constantTimeEquals(hmac(paymentId + "|" + subscriptionId, keySecret), signature);
    }

    public String createPlan(String period, int interval, String itemName, long amountPaise, Map<String, String> notes) {
        try {
            JSONObject item = new JSONObject();
            item.put("name", itemName);
            item.put("amount", amountPaise);
            item.put("currency", currency());
            JSONObject request = new JSONObject();
            request.put("period", period);
            request.put("interval", interval);
            request.put("item", item);
            request.put("notes", new JSONObject(notes));
            com.razorpay.Plan plan = client().plans.create(request);
            return plan.get("id");
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to create the Razorpay plan: " + ex.getMessage());
        }
    }

    public JSONObject createSubscription(String planId, int quantity, int totalCount, Long startAtEpochSeconds, Map<String, String> notes) {
        try {
            JSONObject request = new JSONObject();
            request.put("plan_id", planId);
            request.put("quantity", quantity);
            request.put("total_count", totalCount);
            request.put("customer_notify", 1);
            if (startAtEpochSeconds != null) request.put("start_at", startAtEpochSeconds);
            request.put("notes", new JSONObject(notes));
            return client().subscriptions.create(request).toJson();
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to start auto-debit: " + ex.getMessage());
        }
    }

    public void cancelSubscription(String subscriptionId) {
        try {
            JSONObject request = new JSONObject();
            request.put("cancel_at_cycle_end", 0);
            client().subscriptions.cancel(subscriptionId, request);
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to cancel auto-debit: " + ex.getMessage());
        }
    }

    static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new BadRequestException("Unable to verify Razorpay signature.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
