package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.leave.service.LeaveRequestService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantToolRegistryTest {
    @Mock private LeaveRequestService leaveRequestService;
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
                deviceEnrollmentService, attendanceRecordRepository, employeeSalaryService, authorizationService);
    }
}
