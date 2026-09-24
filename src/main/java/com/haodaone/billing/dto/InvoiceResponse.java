package com.haodaone.billing.dto;

import com.haodaone.billing.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String invoiceType,
        String status,
        LocalDate invoiceDate,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        String plan,
        Integer employeeCount,
        String billingCycle,
        BigDecimal unitRate,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String paymentMethod,
        String razorpayOrderId,
        String razorpayPaymentId,
        String emailStatus,
        String companyName,
        String companyLegalName,
        String billingAddress,
        String gstin
) {
    public static InvoiceResponse from(Invoice invoice) {
        var company = invoice.getCompany();
        return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceType().name(), invoice.getStatus().name(),
                invoice.getInvoiceDate(), invoice.getBillingPeriodStart(), invoice.getBillingPeriodEnd(), invoice.getPlan(),
                invoice.getEmployeeCount(), invoice.getBillingCycle(), invoice.getUnitRate(), invoice.getSubtotal(),
                invoice.getDiscount(), invoice.getTax(), invoice.getTotal(), invoice.getCurrency(), invoice.getPaymentMethod(),
                invoice.getRazorpayOrderId(), invoice.getRazorpayPaymentId(), invoice.getEmailStatus(),
                company == null ? null : company.getName(),
                company == null ? null : company.getLegalName(),
                company == null ? null : company.getBillingAddress(),
                company == null ? null : company.getGstin());
    }
}
