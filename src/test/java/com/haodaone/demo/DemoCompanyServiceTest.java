package com.haodaone.demo;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.demo.service.DemoCompanyService;
import com.haodaone.demo.service.DemoCompanyService.DemoCredentials;
import com.haodaone.recruitment.service.EmailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seeds a demo company on H2, checks the counts, and that reset is repeatable without duplicates. */
@SpringBootTest
@ActiveProfiles("test")
class DemoCompanyServiceTest {

    @Autowired private DemoCompanyService demoCompanyService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @PersistenceContext private EntityManager em;
    @MockBean private EmailService emailService;

    @Test
    void seedsARealisticCompanyAndResetIsIdempotent() {
        DemoCredentials created = demoCompanyService.create("Demo Test " + UUID.randomUUID());
        Long companyId = created.companyId();
        Company company = companyRepository.findById(companyId).orElseThrow();
        assertTrue(company.isDemo());

        Map<String, Long> counts = created.counts();
        assertEquals(40L, counts.get("employees"));
        assertEquals(7L, counts.get("departments"));
        assertEquals(2L, counts.get("payrollRuns"));
        assertEquals(4L, counts.get("jobOpenings"));
        assertEquals(18L, counts.get("candidates"));
        assertEquals(8L, counts.get("expenseClaims"));
        assertTrue(counts.get("attendanceSessions") > 1000, "attendance: " + counts.get("attendanceSessions"));
        assertTrue(counts.get("leaveRequests") > 10);

        Map<String, Long> live = liveCounts(companyId);
        assertEquals(40L, live.get("employees"));
        assertEquals(2L, live.get("processedRuns"));
        assertEquals(1L, live.get("checklists"));
        assertTrue(live.get("payrollItems") >= 35, "payroll items: " + live.get("payrollItems"));
        assertEquals(counts.get("attendanceSessions"), live.get("attendance"));

        DemoCredentials first = demoCompanyService.reset(companyId);
        Map<String, Long> afterFirst = liveCounts(companyId);
        DemoCredentials second = demoCompanyService.reset(companyId);
        Map<String, Long> afterSecond = liveCounts(companyId);

        // Attendance is seeded up to "today", so a run that crosses midnight adds a day; compare it separately.
        assertEquals(withoutAttendance(live), withoutAttendance(afterFirst));
        assertEquals(withoutAttendance(afterFirst), withoutAttendance(afterSecond));
        assertTrue(Math.abs(afterSecond.get("attendance") - live.get("attendance")) <= 60,
                () -> "attendance drifted more than a day: " + live.get("attendance") + " vs " + afterSecond.get("attendance"));
        assertEquals(withoutAttendance(first.counts()), withoutAttendance(second.counts()));
        assertEquals(withoutAttendance(created.counts()), withoutAttendance(second.counts()));
        assertNotEquals(created.adminUsername(), second.adminUsername());
        assertEquals(1L, afterSecond.get("activeAdmins"), "old demo logins must be disabled");

        String hash = transactionTemplate.execute(status -> em.createQuery(
                        "SELECT u.passwordHash FROM User u WHERE u.username = :u", String.class)
                .setParameter("u", second.adminUsername()).getSingleResult());
        assertTrue(passwordEncoder.matches(second.adminPassword(), hash));
        assertEquals(1, demoCompanyService.list().stream().filter(d -> d.id().equals(companyId)).count());
    }

    @Test
    void resetRefusesARealCompany() {
        Company real = new Company();
        real.setName("Real Co " + UUID.randomUUID());
        Long id = companyRepository.save(real).getId();
        assertThrows(BadRequestException.class, () -> demoCompanyService.reset(id));
    }

    private Map<String, Long> liveCounts(Long companyId) {
        return transactionTemplate.execute(status -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("employees", count("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :c AND e.deleted = false", companyId));
            counts.put("departments", count("SELECT COUNT(d) FROM Department d WHERE d.company.id = :c AND d.deleted = false", companyId));
            counts.put("processedRuns", count("SELECT COUNT(r) FROM PayrollRun r WHERE r.company.id = :c AND r.deleted = false "
                    + "AND r.status = 'PROCESSED'", companyId));
            counts.put("payrollItems", count("SELECT COUNT(i) FROM PayrollItem i WHERE i.payrollRun.company.id = :c "
                    + "AND i.deleted = false AND i.payrollRun.deleted = false", companyId));
            counts.put("attendance", count("SELECT COUNT(a) FROM AttendanceSession a WHERE a.company.id = :c AND a.deleted = false", companyId));
            counts.put("leaveTypes", count("SELECT COUNT(t) FROM LeaveType t WHERE t.company.id = :c AND t.deleted = false", companyId));
            counts.put("checklists", count("SELECT COUNT(c) FROM EmployeeChecklist c WHERE c.company.id = :c AND c.deleted = false", companyId));
            counts.put("activeAdmins", count("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE u.company.id = :c "
                    + "AND u.active = true AND u.deleted = false AND r.name = 'COMPANY_ADMIN'", companyId));
            return counts;
        });
    }

    private long count(String jpql, Long companyId) {
        return em.createQuery(jpql, Long.class).setParameter("c", companyId).getSingleResult();
    }

    private static Map<String, Long> withoutAttendance(Map<String, ? extends Number> counts) {
        Map<String, Long> copy = new java.util.TreeMap<>();
        counts.forEach((key, value) -> {
            if (!key.toLowerCase().contains("attendance")) copy.put(key, value.longValue());
        });
        return copy;
    }
}
