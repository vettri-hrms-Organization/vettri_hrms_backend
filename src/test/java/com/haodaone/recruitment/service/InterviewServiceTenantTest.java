package com.haodaone.recruitment.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.recruitment.dto.InterviewDTO;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.InterviewRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewServiceTenantTest {
    private InterviewRepository interviewRepository;
    private CandidateRepository candidateRepository;
    private EmployeeRepository employeeRepository;
    private InterviewService service;

    @BeforeEach
    void setUp() {
        interviewRepository = mock(InterviewRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        service = new InterviewService(
                interviewRepository,
                candidateRepository,
                employeeRepository,
                mock(AuditLogService.class),
                mock(EmailService.class),
                mock(AuthorizationService.class));
        TenantContext.setCurrentTenant(5L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void candidateInterviewsAreQueriedWithinCurrentCompany() {
        when(interviewRepository.findAllByCandidate_IdAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtDesc(42L, 5L))
                .thenReturn(List.of());

        service.byCandidate(42L);

        verify(interviewRepository).findAllByCandidate_IdAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtDesc(42L, 5L);
        verify(interviewRepository, never()).findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(anyLong());
    }

    @Test
    void upcomingInterviewsAreQueriedWithinCurrentCompany() {
        when(interviewRepository.findAllByStatusAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtAsc("SCHEDULED", 5L))
                .thenReturn(List.of());

        service.upcoming();

        verify(interviewRepository).findAllByStatusAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtAsc("SCHEDULED", 5L);
        verify(interviewRepository, never()).findAllByStatusOrderByScheduledAtAsc("SCHEDULED");
    }

    @Test
    void feedbackCannotLoadInterviewFromAnotherCompany() {
        when(interviewRepository.findByIdAndCandidate_JobOpening_Company_IdAndDeletedFalse(99L, 5L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.submitFeedback(99L, new InterviewDTO.FeedbackRequest()));

        verify(interviewRepository, never()).findById(anyLong());
    }

    @Test
    void scheduleCannotLoadCandidateFromAnotherCompany() {
        when(candidateRepository.findByIdAndJobOpening_Company_IdAndDeletedFalse(99L, 5L))
                .thenReturn(Optional.empty());
        InterviewDTO.CreateRequest request = new InterviewDTO.CreateRequest();
        request.setCandidateId(99L);
        request.setScheduledAt(LocalDateTime.now());
        request.setRoundNumber(1);

        assertThrows(BadRequestException.class, () -> service.schedule(request));

        verify(candidateRepository, never()).findById(anyLong());
    }
}
