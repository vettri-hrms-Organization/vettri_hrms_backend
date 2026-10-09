package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.salary.service.EmployeeSalaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantToolRegistryTest {
    @Mock private LeaveRequestService leaveRequestService;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private AssistantConversationStore conversationStore;
    @Mock private EmployeeDocumentService employeeDocumentService;
    @Mock private DeviceEnrollmentService deviceEnrollmentService;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private EmployeeSalaryService employeeSalaryService;
    @Mock private AuthorizationService authorizationService;

    @AfterEach
    void cleanup() {
        com.haodaone.tenant.TenantContext.clear();
    }

    @Test
    void personalLeaveToolUsesOnlyAuthenticatedEmployeeAndRejectsModelEmployeeId() {
        com.haodaone.tenant.TenantContext.setCurrentTenant(42L);
        when(authorizationService.isAllowed("SELF_LEAVE_VIEW", "EMPLOYEE", 51L)).thenReturn(true);
        var registry = registry();
        AssistantContext context = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_PROFILE_VIEW", "SELF_LEAVE_VIEW"), Set.of("EMPLOYEE"), null, null, null);

        assertThat(registry.availableTools(context).stream().map(AiProvider.ModelTool::name))
                .contains("get_my_leave_balance");
        assertThatThrownBy(() -> registry.execute("get_my_leave_balance", Map.of("employeeId", 99), context))
                .isInstanceOf(AssistantToolException.class);
        verify(leaveRequestService, never()).getBalances(anyLong(), anyInt());
    }

    @Test
    void employeeWithoutEffectivePermissionCannotAccessLiveLeaveTool() {
        var registry = registry();
        AssistantContext context = new AssistantContext(7L, 51L, 42L,
                Set.of("ROLE_EMPLOYEE"), Set.of("EMPLOYEE"), null, null, null);

        assertThat(registry.availableTools(context).stream().map(AiProvider.ModelTool::name))
                .doesNotContain("get_my_leave_balance");
        assertThatThrownBy(() -> registry.execute("get_my_leave_balance", Map.of(), context))
                .isInstanceOf(AssistantToolException.class)
                .satisfies(error -> assertThat(((AssistantToolException) error).isForbidden()).isTrue());
        verify(leaveRequestService, never()).getBalances(anyLong(), anyInt());
    }

    @Test
    void readOnlyToolsUseStrictInputSchemasWithoutArbitraryIdentityFields() {
        when(authorizationService.isAllowed("SELF_ATTENDANCE_VIEW", "EMPLOYEE", 51L)).thenReturn(true);
        var registry = registry();
        AssistantContext context = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW"), Set.of("EMPLOYEE"), null, null, null);
        var tool = registry.availableTools(context).stream()
                .filter(candidate -> candidate.name().equals("get_my_attendance"))
                .findFirst().orElseThrow();

        assertThat(tool.parameters()).containsEntry("additionalProperties", false);
        assertThat(tool.parameters().toString()).contains("date").doesNotContain("employeeId", "companyId", "tenantId");
    }

    @Test
    void leavePreparationIsPermissionGatedAndHasNoModelSuppliedArguments() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        var registry = registry();
        AssistantContext allowed = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        AssistantContext denied = new AssistantContext(7L, 51L, 42L,
                Set.of("ROLE_EMPLOYEE"), Set.of("EMPLOYEE"), null, null, null);

        var tool = registry.availableTools(allowed).stream()
                .filter(candidate -> candidate.name().equals("prepare_leave_request"))
                .findFirst().orElseThrow();

        assertThat(tool.parameters()).containsEntry("additionalProperties", false);
        assertThat(tool.parameters().toString()).doesNotContain("employeeId", "companyId", "tenantId", "leaveTypeId");
        assertThat(registry.availableTools(denied).stream().map(AiProvider.ModelTool::name))
                .doesNotContain("prepare_leave_request");
        verifyNoInteractions(leaveTypeRepository, conversationStore);
    }

    @Test
    void leavePreparationReusesLeaveServiceValidationAndDoesNotSubmit() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getId()).thenReturn(12L);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        when(leaveRequestService.preview(any())).thenReturn(new LeaveRequestService.LeaveApplicationPreview(
                51L, 12L, "Casual Leave", LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 9), 1, 3));

        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        UUID conversationId = UUID.randomUUID();
        var result = registry.prepareLeaveRequest(
                conversationId, "Please apply casual leave for 9th October 2026", employee, null);

        assertThat(result.message()).contains("Casual Leave", "9 October 2026", "Available balance: 3 days");
        assertThat(result.action().type()).isEqualTo("LEAVE_CONFIRMATION");
        assertThat(result.action().data()).containsEntry("leaveType", "Casual Leave")
                .containsEntry("startDate", "2026-10-09")
                .containsEntry("remainingDays", 3.0);
        verify(leaveRequestService).preview(argThat(request ->
                request.getEmployeeId().equals(51L)
                        && request.getLeaveTypeId().equals(12L)
                        && request.getStartDate().equals(LocalDate.of(2026, 10, 9))
                        && request.getEndDate().equals(LocalDate.of(2026, 10, 9))));
        verify(leaveRequestService, never()).apply(any());
        verify(conversationStore).savePendingLeave(eq(conversationId), eq(42L), eq(7L), any());
    }

    @Test
    void exactRequestResolvesCompactDateAndPluralCasualLeavesWithoutInventingAReason() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getId()).thenReturn(12L);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        when(leaveRequestService.preview(any())).thenReturn(new LeaveRequestService.LeaveApplicationPreview(
                51L, 12L, "Casual Leave", LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 8), 1, 3));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);

        var result = registry.prepareLeaveRequest(
                UUID.randomUUID(), "can you apply casual leave on 8th oct 2026", employee, null);

        assertThat(result.message()).contains("Casual Leave", "8 October 2026")
                .doesNotContain("Personal reasons");
        assertThat(result.action().data()).containsEntry("startDate", "2026-10-08")
                .containsEntry("endDate", "2026-10-08")
                .doesNotContainKey("reason");
        verify(leaveRequestService).preview(argThat(request ->
                request.getEmployeeId().equals(51L)
                        && request.getLeaveTypeId().equals(12L)
                        && request.getStartDate().equals(LocalDate.of(2026, 10, 8))
                        && request.getEndDate().equals(LocalDate.of(2026, 10, 8))
                        && request.getReason() == null));
        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void requestWithoutLeaveTypeKeepsParsedPastDateAndAsksForTheMissingType() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        UUID conversationId = UUID.randomUUID();

        var result = registry.prepareLeaveRequest(
                conversationId, "please apply leave on 07 oct 2026", employee, null);

        assertThat(result.message()).contains("Which leave type", "Casual Leave");
        verify(conversationStore).savePendingLeave(eq(conversationId), eq(42L), eq(7L),
                argThat(draft -> draft.leaveTypeId() == null
                        && draft.startDate().equals(LocalDate.of(2026, 10, 7))
                        && draft.endDate().equals(LocalDate.of(2026, 10, 7))
                        && !draft.ready()));
        verify(leaveRequestService, never()).preview(any());
        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void unauthorizedLeavePreparationDoesNotReadLeaveTypesOrCreateProposal() {
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("ROLE_EMPLOYEE"), Set.of("EMPLOYEE"), null, null, null);

        var result = registry.prepareLeaveRequest(
                UUID.randomUUID(), "Please apply casual leave tomorrow", employee, null);

        assertThat(result.message()).contains("don't have permission");
        verifyNoInteractions(leaveTypeRepository, leaveRequestService, conversationStore);
    }

    @Test
    void unknownLeaveTypeCreatesOnlyAnIncompleteDraftAndDoesNotValidateOrSubmit() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        UUID conversationId = UUID.randomUUID();

        var result = registry.prepareLeaveRequest(
                conversationId, "Please apply garden leave tomorrow", employee, null);

        assertThat(result.message()).contains("couldn't find that leave type", "Casual Leave");
        verify(conversationStore).savePendingLeave(eq(conversationId), eq(42L), eq(7L),
                argThat(draft -> draft.leaveTypeId() == null
                        && draft.startDate() != null
                        && !draft.ready()));
        verify(leaveRequestService, never()).preview(any());
        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void insufficientBalanceFromVettriPreviewCannotCreateAConfirmableProposal() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getId()).thenReturn(12L);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        when(leaveRequestService.preview(any()))
                .thenThrow(new BadRequestException("Insufficient leave balance"));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);

        var result = registry.prepareLeaveRequest(
                UUID.randomUUID(), "Please apply casual leave tomorrow", employee, null);

        assertThat(result.message()).contains("Insufficient leave balance", "haven't submitted anything");
        assertThat(result.action()).isNull();
        verify(conversationStore, never()).savePendingLeave(any(), anyLong(), anyLong(), any());
        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void incompleteLeaveDraftCanBeCompletedWithADateReply() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getId()).thenReturn(12L);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(leaveRequestService.preview(any())).thenReturn(new LeaveRequestService.LeaveApplicationPreview(
                51L, 12L, "Casual Leave", tomorrow, tomorrow, 1, 3));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        UUID conversationId = UUID.randomUUID();
        var draft = new AssistantConversationStore.PendingLeaveAction(
                51L, 12L, "Casual Leave", null, null, null,
                null, null, false, LocalDateTime.now());

        var result = registry.prepareLeaveRequest(conversationId, "tomorrow", employee, draft);

        assertThat(result.action()).isNotNull();
        assertThat(result.action().type()).isEqualTo("LEAVE_CONFIRMATION");
        verify(leaveRequestService).preview(argThat(request ->
                request.getEmployeeId().equals(51L)
                        && request.getLeaveTypeId().equals(12L)
                        && request.getStartDate().equals(tomorrow)
                        && request.getEndDate().equals(tomorrow)));
    }

    @Test
    void staleLeaveConfirmationNeverInvokesLeaveApplication() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        UUID conversationId = UUID.randomUUID();
        when(conversationStore.lockPendingLeave(conversationId, 42L, 7L)).thenReturn(java.util.Optional.of(
                new AssistantConversationStore.PendingLeaveAction(
                        51L, 12L, "Casual Leave", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, 1.0, 3.0, true, LocalDateTime.now().minusMinutes(30))));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);

        assertThatThrownBy(() -> registry.submitConfirmedLeave(conversationId, employee))
                .isInstanceOf(PendingLeaveExpiredException.class);

        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void confirmedLeaveUsesAuthenticatedEmployeeAndExistingLeaveService() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        UUID conversationId = UUID.randomUUID();
        LocalDate leaveDate = LocalDate.of(2026, 10, 12);
        AssistantConversationStore.PendingLeaveAction pending = new AssistantConversationStore.PendingLeaveAction(
                51L, 12L, "Casual Leave", leaveDate, leaveDate, "Personal work",
                1.0, 3.0, true, LocalDateTime.now());
        when(conversationStore.lockPendingLeave(conversationId, 42L, 7L))
                .thenReturn(java.util.Optional.of(pending));
        com.haodaone.leave.dto.LeaveRequestDTO created = mock(com.haodaone.leave.dto.LeaveRequestDTO.class);
        when(leaveRequestService.apply(any())).thenReturn(created);
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);

        var result = registry.submitConfirmedLeave(conversationId, employee);

        assertThat(result).isSameAs(created);
        verify(leaveRequestService).apply(argThat(request ->
                request.getEmployeeId().equals(51L)
                        && request.getLeaveTypeId().equals(12L)
                        && request.getStartDate().equals(leaveDate)
                        && request.getEndDate().equals(leaveDate)
                        && request.getReason().equals("Personal work")));
        verify(conversationStore).clearPendingLeave(conversationId, 42L, 7L);
    }

    @Test
    void rePreparedLeaveProposalGetsANewValidLifetime() {
        when(authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", 51L)).thenReturn(true);
        com.haodaone.leave.entity.LeaveType casual = mock(com.haodaone.leave.entity.LeaveType.class);
        when(casual.getId()).thenReturn(12L);
        when(casual.getName()).thenReturn("Casual Leave");
        when(casual.isActive()).thenReturn(true);
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(casual));
        LocalDate leaveDate = LocalDate.now().plusDays(1);
        when(leaveRequestService.preview(any())).thenReturn(new LeaveRequestService.LeaveApplicationPreview(
                51L, 12L, "Casual Leave", leaveDate, leaveDate, 1, 3));
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_LEAVE_APPLY"), Set.of("EMPLOYEE"), null, null, null);
        UUID conversationId = UUID.randomUUID();
        var staleProposal = new AssistantConversationStore.PendingLeaveAction(
                51L, 12L, "Casual Leave", leaveDate, leaveDate, null,
                1.0, 3.0, true, LocalDateTime.now().minusHours(1));

        registry.prepareLeaveRequest(
                conversationId, "Please apply casual leave tomorrow", employee, staleProposal);

        verify(conversationStore).savePendingLeave(eq(conversationId), eq(42L), eq(7L),
                argThat(proposal -> proposal.createdAt() != null
                        && !PendingLeaveExpiry.isExpired(proposal.createdAt(), LocalDateTime.now())));
        verify(leaveRequestService, never()).apply(any());
    }

    @Test
    void leaveDateParserResolvesAbsoluteAndRelativeDatesFromTheApplicationDate() {
        LocalDate today = LocalDate.of(2026, 10, 8);

        var absolute = AssistantLeaveDateParser.parse("apply leave for 9th October 2026", today);
        var compactDayMonth = AssistantLeaveDateParser.parse("leave on 8oct 2026", today);
        var tomorrow = AssistantLeaveDateParser.parse("apply leave tomorrow", today);
        var weekday = AssistantLeaveDateParser.parse("apply leave next Monday", today);

        assertThat(absolute).isEqualTo(new AssistantLeaveDateParser.DateRange(
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9)));
        assertThat(compactDayMonth).isEqualTo(new AssistantLeaveDateParser.DateRange(
                LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8)));
        assertThat(tomorrow.startDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(weekday.startDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThatThrownBy(() -> AssistantLeaveDateParser.parse("apply leave for the 9th", today))
                .isInstanceOf(AssistantLeaveDateParser.AmbiguousDateException.class);
    }

    @Test
    void salaryToolsRequireSelfScopeAndDoNotAcceptAnEmployeeId() {
        when(authorizationService.isAllowed("SELF_PAYSLIP_VIEW", "EMPLOYEE", 51L)).thenReturn(true);
        var registry = registry();
        AssistantContext context = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_PROFILE_VIEW", "SELF_PAYSLIP_VIEW"), Set.of("EMPLOYEE"), null, null, null);

        var tool = registry.availableTools(context).stream()
                .filter(candidate -> candidate.name().equals("get_my_salary"))
                .findFirst().orElseThrow();
        assertThat(tool.parameters()).containsEntry("additionalProperties", false);

        assertThatThrownBy(() -> registry.execute("get_my_salary", Map.of("employeeId", 99), context))
                .isInstanceOf(AssistantToolException.class);
        verify(employeeSalaryService, never()).getDetail(anyLong());

        AssistantContext unauthorized = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_PROFILE_VIEW"), Set.of("EMPLOYEE"), null, null, null);
        assertThat(registry.availableTools(unauthorized).stream().map(AiProvider.ModelTool::name))
                .doesNotContain("get_my_salary", "get_my_payslip");
        assertThatThrownBy(() -> registry.execute("get_my_salary", Map.of(), unauthorized))
                .isInstanceOf(AssistantToolException.class);
    }

    @Test
    void deviceMappingGuidanceDeniesEmployeeWithoutManagementPermissionWithoutReadingDeviceData() {
        var registry = registry();
        AssistantContext employee = new AssistantContext(7L, 51L, 42L,
                Set.of("SELF_PROFILE_VIEW"), Set.of("EMPLOYEE"), null, null, null);

        AssistantToolRegistry.DeviceMappingGuidance guidance = registry.deviceMappingGuidance(employee);

        assertThat(guidance.message()).contains("don't have permission to map devices");
        assertThat(guidance.action()).isNull();
        verifyNoInteractions(deviceEnrollmentService);
    }

    @Test
    void authorizedItUserGetsVerifiedMappingStepsAndAllowListedNavigation() {
        when(authorizationService.isAllowed("IT_MANAGEMENT_ACCESS", null, null)).thenReturn(true);
        when(authorizationService.isAllowed("MONITORING_VIEW", null, null)).thenReturn(true);
        when(authorizationService.hasOrganizationScope("MONITORING_VIEW")).thenReturn(true);
        when(authorizationService.isAllowed("MONITORING_MANAGE", null, null)).thenReturn(true);
        when(authorizationService.hasOrganizationScope("MONITORING_MANAGE")).thenReturn(true);
        var registry = registry();
        AssistantContext itUser = new AssistantContext(7L, 51L, 42L,
                Set.of("IT_MANAGEMENT_ACCESS", "MONITORING_VIEW", "MONITORING_MANAGE"),
                Set.of("IT_ADMINISTRATOR"), null, null, null);

        AssistantToolRegistry.DeviceMappingGuidance guidance = registry.deviceMappingGuidance(itUser);
        AssistantToolRegistry.ToolResult navigation = registry.execute(
                "get_navigation_guidance", Map.of("topic", "devices"), itUser);

        assertThat(guidance.message()).contains(
                "Open IT Management → Devices",
                "choose Map device",
                "choose Change employee",
                "Select the employee",
                "Confirm assignment");
        assertThat(guidance.action()).isNotNull()
                .extracting(com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction::route)
                .isEqualTo("/monitoring/devices");
        assertThat(navigation.action()).isNotNull()
                .extracting(com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction::route)
                .isEqualTo("/monitoring/devices");
        assertThat(navigation.sanitizedJson()).contains("Confirm assignment");
        verifyNoInteractions(deviceEnrollmentService);
    }

    @Test
    void deviceViewPermissionAllowsNavigationButDoesNotGrantMapping() {
        when(authorizationService.isAllowed("IT_MANAGEMENT_ACCESS", null, null)).thenReturn(true);
        when(authorizationService.isAllowed("MONITORING_VIEW", null, null)).thenReturn(true);
        when(authorizationService.hasOrganizationScope("MONITORING_VIEW")).thenReturn(true);
        var registry = registry();
        AssistantContext viewOnly = new AssistantContext(7L, 51L, 42L,
                Set.of("IT_MANAGEMENT_ACCESS", "MONITORING_VIEW"), Set.of("IT_VIEWER"), null, null, null);

        AssistantToolRegistry.DeviceMappingGuidance guidance = registry.deviceMappingGuidance(viewOnly);

        assertThat(guidance.message()).contains("don't have permission to map devices");
        assertThat(guidance.action()).isNotNull()
                .extracting(com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction::route)
                .isEqualTo("/monitoring/devices");
        verifyNoInteractions(deviceEnrollmentService);
    }

    private AssistantToolRegistry registry() {
        return new AssistantToolRegistry(new ObjectMapper(), leaveRequestService, employeeDocumentService,
                deviceEnrollmentService, attendanceRecordRepository, employeeSalaryService, authorizationService,
                leaveTypeRepository, conversationStore);
    }
}
