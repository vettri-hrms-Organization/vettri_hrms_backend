package com.haodaone.company.repository;

import com.haodaone.company.entity.Subscription;
import org.springframework.stereotype.Service;
import com.haodaone.common.exception.BadRequestException;

import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public Optional<Subscription> getForCompany(Long companyId) {
        if (companyId == null) return Optional.empty();
        return subscriptionRepository.findByCompany_IdAndDeletedFalse(companyId);
    }

    public void ensureDeviceLimitNotExceeded(long companyId, long currentDeviceCount) {
        Optional<Subscription> s = getForCompany(companyId);
        if (s.isEmpty()) return;
        Subscription sub = s.get();
        if (sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.ACTIVE && sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.PAST_DUE && sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.TRIAL && sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.TRIALING) {
            throw new BadRequestException("Company subscription status " + sub.getStatus() + " does not allow adding devices");
        }
        Integer limit = sub.getDeviceLimit();
        if (limit != null && currentDeviceCount >= limit) {
            throw new BadRequestException("Device limit reached for plan " + sub.getPlan() + " (limit: " + limit + ")");
        }
    }

    /**
     * Seat policy:
     *  - trials: employeeLimit is a hard limit;
     *  - paid (ACTIVE / PAST_DUE): the paid seat count is a SOFT limit - extra seats are allowed and billed as
     *    a prorated add-on on the next invoice (BillingLifecycleService.recordSeatAddOns); the hard cap is
     *    employeeLimit, further capped by app.billing.seat-hard-cap when that is > 0;
     *  - suspended / expired / cancelled / pending payment: no new employees.
     * currentEmployeeCount is the caller's count; when available the billable seat count
     * (excluding Resigned / Terminated) is used instead.
     */
    public void ensureEmployeeLimitNotExceeded(long companyId, long currentEmployeeCount) {
        Optional<Subscription> s = getForCompany(companyId);
        if (s.isEmpty()) return;
        Subscription sub = s.get();
        com.haodaone.company.entity.SubscriptionStatus status = sub.getStatus();
        boolean trial = status == com.haodaone.company.entity.SubscriptionStatus.TRIAL || status == com.haodaone.company.entity.SubscriptionStatus.TRIALING;
        boolean paid = status == com.haodaone.company.entity.SubscriptionStatus.ACTIVE || status == com.haodaone.company.entity.SubscriptionStatus.PAST_DUE;
        if (!trial && !paid) {
            throw new BadRequestException("Your subscription is inactive (" + status + "). Pay the due invoice under Billing to add employees.");
        }
        long count = seatCounter != null ? seatCounter.billableSeats(companyId) : currentEmployeeCount;
        Integer limit = sub.getEmployeeLimit();
        if (trial) {
            if (limit != null && count >= limit) {
                throw new BadRequestException("Employee limit reached for your trial (limit: " + limit + "). Choose a plan under Billing to add more.");
            }
            return;
        }
        int hardCap = limit == null ? Integer.MAX_VALUE : limit;
        if (seatHardCap > 0) hardCap = Math.min(hardCap, seatHardCap);
        if (count >= hardCap) {
            throw new BadRequestException("Employee limit reached for plan " + sub.getPlan() + " (limit: " + hardCap
                    + "). Contact support to upgrade seats.");
        }
    }

    private com.haodaone.billing.service.SeatCountService seatCounter;

    @org.springframework.beans.factory.annotation.Value("${app.billing.seat-hard-cap:0}")
    private int seatHardCap;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSeatCounter(com.haodaone.billing.service.SeatCountService seatCounter) {
        this.seatCounter = seatCounter;
    }
}
