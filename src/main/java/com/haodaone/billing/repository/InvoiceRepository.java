package com.haodaone.billing.repository;

import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByPaymentTransaction_Id(Long paymentTransactionId);
    Optional<Invoice> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    List<Invoice> findByCompany_IdAndDeletedFalseOrderByInvoiceDateDescIdDesc(Long companyId, Pageable pageable);
    List<Invoice> findByCompany_IdAndStatusAndDeletedFalseOrderByInvoiceDateDescIdDesc(Long companyId, InvoiceStatus status, Pageable pageable);
    List<Invoice> findByCompany_IdAndInvoiceNumberContainingIgnoreCaseAndDeletedFalseOrderByInvoiceDateDescIdDesc(Long companyId, String query, Pageable pageable);
    Optional<Invoice> findFirstBySubscription_IdAndStatusAndInvoiceTypeAndDeletedFalseOrderByIdDesc(Long subscriptionId, InvoiceStatus status, com.haodaone.billing.entity.InvoiceType invoiceType);
    Optional<Invoice> findFirstByRazorpayOrderIdAndDeletedFalse(String razorpayOrderId);
    boolean existsBySubscription_IdAndInvoiceTypeAndStatusAndDeletedFalse(Long subscriptionId, com.haodaone.billing.entity.InvoiceType invoiceType, InvoiceStatus status);
    List<Invoice> findByOriginalInvoice_IdAndDeletedFalse(Long originalInvoiceId);
    @Query(value = "select nextval('billing_invoice_number_seq')", nativeQuery = true)
    Long nextInvoiceSequence();
}
