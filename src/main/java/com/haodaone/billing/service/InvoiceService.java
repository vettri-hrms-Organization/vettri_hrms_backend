package com.haodaone.billing.service;

import com.haodaone.billing.dto.InvoiceResponse;
import com.haodaone.billing.entity.*;
import com.haodaone.billing.repository.InvoiceRepository;
import com.haodaone.billing.repository.PaymentTransactionRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.recruitment.service.EmailService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {
    private static final BigDecimal VERIFICATION_AMOUNT = new BigDecimal("1.00");

    private final InvoiceRepository invoices;
    private final PaymentTransactionRepository payments;
    private final InvoicePdfService pdfService;
    private final EmailService emailService;

    public InvoiceService(InvoiceRepository invoices, PaymentTransactionRepository payments,
                          InvoicePdfService pdfService, EmailService emailService) {
        this.invoices = invoices;
        this.payments = payments;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    @Transactional
    public Optional<Invoice> createIfMissing(PaymentTransaction payment) {
        if (payment == null || payment.getId() == null) return Optional.empty();
        if (payment.getStatus() != PaymentTransactionStatus.VERIFIED) return Optional.empty();
        if (invoices.findByPaymentTransaction_Id(payment.getId()).isPresent()) return Optional.empty();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(String.format("INV-%d-%06d", LocalDate.now().getYear(), invoices.nextInvoiceSequence()));
        invoice.setCompany(payment.getCompany());
        invoice.setSubscription(payment.getSubscription());
        invoice.setPaymentTransaction(payment);
        invoice.setInvoiceType(VERIFICATION_AMOUNT.compareTo(payment.getAmount()) == 0 ? InvoiceType.PAYMENT_VERIFICATION_RECEIPT : InvoiceType.SUBSCRIPTION);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setInvoiceDate(payment.getPaidAt() == null ? LocalDate.now() : payment.getPaidAt().toLocalDate());
        var subscription = payment.getSubscription();
        invoice.setBillingPeriodStart(subscription == null ? null : subscription.getStartDate());
        invoice.setBillingPeriodEnd(subscription == null ? null : subscription.getRenewalDate() == null ? null : subscription.getRenewalDate().minusDays(1));
        invoice.setPlan(payment.getPlan());
        invoice.setEmployeeCount(subscription == null ? null : subscription.getBillableEmployeeCount());
        invoice.setBillingCycle(subscription == null ? null : subscription.getBillingCycle());
        invoice.setUnitRate(subscription == null ? null : subscription.getRate());
        invoice.setSubtotal(payment.getAmount());
        invoice.setDiscount(BigDecimal.ZERO);
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(payment.getAmount());
        invoice.setCurrency(payment.getCurrency());
        invoice.setPaymentMethod(payment.getPaymentMethod());
        invoice.setRazorpayOrderId(payment.getRazorpayOrderId());
        invoice.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        invoice.setEmailStatus("NOT_SENT");
        return Optional.of(invoices.save(invoice));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(Long companyId, String status, String query, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Direction.DESC, "invoiceDate", "id"));
        List<Invoice> result;
        if (companyId == null) result = invoices.findAll(page).getContent();
        else if (query != null && !query.isBlank()) result = invoices.findByCompany_IdAndInvoiceNumberContainingIgnoreCaseAndDeletedFalseOrderByInvoiceDateDescIdDesc(companyId, query.trim(), page);
        else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) result = invoices.findByCompany_IdAndStatusAndDeletedFalseOrderByInvoiceDateDescIdDesc(companyId, InvoiceStatus.valueOf(status.toUpperCase()), page);
        else result = invoices.findByCompany_IdAndDeletedFalseOrderByInvoiceDateDescIdDesc(companyId, page);
        return result.stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Invoice get(Long id, Long companyId) {
        if (companyId == null) return invoices.findById(id).orElseThrow(() -> new BadRequestException("Invoice not found."));
        return invoices.findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElseThrow(() -> new BadRequestException("Invoice not found."));
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id, Long companyId) { return pdfService.generate(get(id, companyId)); }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void email(Long id, Long companyId, String recipientEmail, String recipientName) {
        Invoice invoice = get(id, companyId);
        String documentLabel = invoice.getInvoiceType() == InvoiceType.PAYMENT_VERIFICATION_RECEIPT
            ? "Payment Verification Receipt" : "Invoice";
        boolean sent = emailService.sendInvoiceEmail(recipientEmail, recipientName, invoice.getInvoiceNumber(), documentLabel, pdfService.generate(invoice));
        invoice.setEmailStatus(sent ? "SENT" : "FAILED");
        invoices.save(invoice);
        if (!sent) throw new BadRequestException("Invoice email could not be delivered. The invoice remains available for download.");
    }
}
