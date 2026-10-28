package com.haodaone.billing.service;

import com.haodaone.employee.entity.EmploymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Billable seats = employees of the company that are not soft-deleted and
 * not Resigned / Terminated (any casing alias of those statuses).
 */
@Service
public class SeatCountService {

    private static final Set<String> NON_BILLABLE;

    static {
        Set<String> statuses = new LinkedHashSet<>();
        statuses.addAll(EmploymentStatus.aliases(EmploymentStatus.RESIGNED));
        statuses.addAll(EmploymentStatus.aliases(EmploymentStatus.TERMINATED));
        NON_BILLABLE = Set.copyOf(statuses);
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public int billableSeats(Long companyId) {
        if (companyId == null) return 0;
        Long count = entityManager.createQuery(
                        "select count(e) from Employee e where e.company.id = :companyId and e.deleted = false "
                                + "and (e.status is null or e.status not in :excluded)", Long.class)
                .setParameter("companyId", companyId)
                .setParameter("excluded", NON_BILLABLE)
                .getSingleResult();
        return count == null ? 0 : count.intValue();
    }
}
