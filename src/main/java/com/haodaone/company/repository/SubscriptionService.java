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
        if (sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.ACTIVE && sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.TRIAL) {
            throw new BadRequestException("Company subscription status " + sub.getStatus() + " does not allow adding devices");
        }
        Integer limit = sub.getDeviceLimit();
        if (limit != null && currentDeviceCount >= limit) {
            throw new BadRequestException("Device limit reached for plan " + sub.getPlan() + " (limit: " + limit + ")");
        }
    }

    public void ensureEmployeeLimitNotExceeded(long companyId, long currentEmployeeCount) {
        Optional<Subscription> s = getForCompany(companyId);
        if (s.isEmpty()) return;
        Subscription sub = s.get();
        if (sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.ACTIVE && sub.getStatus() != com.haodaone.company.entity.SubscriptionStatus.TRIAL) {
            throw new BadRequestException("Company subscription status " + sub.getStatus() + " does not allow adding employees");
        }
        Integer limit = sub.getEmployeeLimit();
        if (limit != null && currentEmployeeCount >= limit) {
            throw new BadRequestException("Employee limit reached for plan " + sub.getPlan() + " (limit: " + limit + ")");
        }
    }
}
