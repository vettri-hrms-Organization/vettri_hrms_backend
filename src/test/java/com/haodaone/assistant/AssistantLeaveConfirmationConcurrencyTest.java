package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.salary.service.EmployeeSalaryService;
import com.haodaone.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantLeaveConfirmationConcurrencyTest {
    @Test
    void concurrentConfirmationsSubmitAtMostOnce() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:assistant-race-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE company (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE app_user (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE leave_type (id BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO company (id) VALUES (42)");
        jdbc.execute("INSERT INTO app_user (id) VALUES (7)");
        jdbc.execute("INSERT INTO employee (id) VALUES (51)");
        jdbc.execute("INSERT INTO leave_type (id) VALUES (12)");
        jdbc.execute("""
                CREATE TABLE assistant_conversation (
                    id UUID PRIMARY KEY,
                    company_id BIGINT NOT NULL REFERENCES company(id),
                    user_id BIGINT NOT NULL REFERENCES app_user(id),
                    title VARCHAR(200) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    archived BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.execute("""
                CREATE TABLE assistant_pending_leave_action (
                    conversation_id UUID PRIMARY KEY REFERENCES assistant_conversation(id) ON DELETE CASCADE,
                    company_id BIGINT NOT NULL REFERENCES company(id),
                    user_id BIGINT NOT NULL REFERENCES app_user(id),
                    employee_id BIGINT NOT NULL REFERENCES employee(id),
                    leave_type_id BIGINT REFERENCES leave_type(id),
                    leave_type_name VARCHAR(100),
                    start_date DATE,
                    end_date DATE,
                    reason VARCHAR(500),
                    requested_days DOUBLE PRECISION,
                    remaining_days DOUBLE PRECISION,
                    is_ready BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP NOT NULL
                )
                """);

        CountDownLatch firstSubmissionStarted = new CountDownLatch(1);
        CountDownLatch allowFirstSubmissionToFinish = new CountDownLatch(1);
        CountDownLatch secondLockAttempted = new CountDownLatch(1);
        AssistantConversationStore store = new AssistantConversationStore(jdbc) {
            @Override
            public Optional<PendingLeaveAction> lockPendingLeave(UUID id, long companyId, long userId) {
                if ("second-confirmation".equals(Thread.currentThread().getName())) {
                    secondLockAttempted.countDown();
                }
                return super.lockPendingLeave(id, companyId, userId);
            }
        };
        UUID conversationId = store.create(42L, 7L);
        LocalDate leaveDate = LocalDate.of(2026, 10, 15);
        store.savePendingLeave(conversationId, 42L, 7L, new AssistantConversationStore.PendingLeaveAction(
                51L, 12L, "Casual Leave", leaveDate, leaveDate, null,
                1.0, 3.0, true, LocalDateTime.of(2026, 10, 9, 17, 0)));

        LeaveRequestService leaveRequestService = mock(LeaveRequestService.class);
        LeaveRequestDTO submitted = mock(LeaveRequestDTO.class);
        AtomicInteger applicationCount = new AtomicInteger();
        when(leaveRequestService.apply(any())).thenAnswer(invocation -> {
            applicationCount.incrementAndGet();
            firstSubmissionStarted.countDown();
            if (!allowFirstSubmissionToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the first confirmation");
            }
            return submitted;
        });
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        AssistantToolRegistry registry = new AssistantToolRegistry(
                new ObjectMapper(),
                leaveRequestService,
                mock(EmployeeDocumentService.class),
                mock(DeviceEnrollmentService.class),
                mock(AttendanceRecordRepository.class),
                mock(EmployeeSalaryService.class),
                authorizationService,
                mock(LeaveTypeRepository.class),
                store,
                Clock.fixed(Instant.parse("2026-10-09T11:31:00Z"), ZoneId.of("Asia/Kolkata"))
        );
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> transactions.execute(status -> {
                registry.submitConfirmedLeave(conversationId, employee);
                return true;
            }));
            assertThat(firstSubmissionStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> {
                Thread.currentThread().setName("second-confirmation");
                return transactions.execute(status -> {
                    try {
                        registry.submitConfirmedLeave(conversationId, employee);
                        return true;
                    } catch (AssistantToolException ex) {
                        return false;
                    }
                });
            });
            assertThat(secondLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            allowFirstSubmissionToFinish.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
            assertThat(applicationCount).hasValue(1);
            assertThat(store.pendingLeave(conversationId, 42L, 7L)).isEmpty();
        } finally {
            allowFirstSubmissionToFinish.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
