package com.haodaone.billing.repository;

import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.entity.PaymentTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    boolean existsByRazorpayOrderId(String razorpayOrderId);

    boolean existsByRazorpayPaymentId(String razorpayPaymentId);

    Optional<PaymentTransaction> findByCompany_IdAndRazorpayPaymentId(Long companyId, String razorpayPaymentId);

    Optional<PaymentTransaction> findByCompany_IdAndRazorpayOrderId(Long companyId, String razorpayOrderId);

    Optional<PaymentTransaction> findByCompany_IdAndStatus(Long companyId, PaymentTransactionStatus status);
}
