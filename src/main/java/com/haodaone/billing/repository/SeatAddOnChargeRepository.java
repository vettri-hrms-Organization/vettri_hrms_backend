package com.haodaone.billing.repository;

import com.haodaone.billing.entity.SeatAddOnCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatAddOnChargeRepository extends JpaRepository<SeatAddOnCharge, Long> {
    List<SeatAddOnCharge> findBySubscription_IdAndInvoiceIsNullAndDeletedFalseOrderByEffectiveDateAsc(Long subscriptionId);
    List<SeatAddOnCharge> findByInvoice_IdAndDeletedFalseOrderByEffectiveDateAsc(Long invoiceId);
}
