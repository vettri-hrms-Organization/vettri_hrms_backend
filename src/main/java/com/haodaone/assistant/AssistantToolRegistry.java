package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.AttendanceRecordDTO;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.salary.dto.EmployeeSalaryDetailDTO;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.service.EmployeeSalaryService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class AssistantToolRegistry {
    private static final int MAX_TOOL_ITEMS = 50;
    private static final List<String> TEAM_SCOPE_NAMES = List.of("TEAM", "DEPARTMENT", "ORGANIZATION");

    private final ObjectMapper objectMapper;
    private final LeaveRequestService leaveRequestService;
    private final EmployeeDocumentService employeeDocumentService;
    private final DeviceEnrollmentService deviceEnrollmentService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AuthorizationService authorizationService;
    private final EmployeeSalaryService employeeSalaryService;

    public AssistantToolRegistry(
            ObjectMapper objectMapper,
            LeaveRequestService leaveRequestService,
            EmployeeDocumentService employeeDocumentService,
            DeviceEnrollmentService deviceEnrollmentService,
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeSalaryService employeeSalaryService,
            AuthorizationService authorizationService
    ) {
        this.objectMapper = objectMapper;
        this.leaveRequestService = leaveRequestService;
        this.employeeDocumentService = employeeDocumentService;
        this.deviceEnrollmentService = deviceEnrollmentService;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeSalaryService = employeeSalaryService;
        this.authorizationService = authorizationService;
    }

    public List<AiProvider.ModelTool> availableTools(AssistantContext context) {
        List<AiProvider.ModelTool> tools = new ArrayList<>();
        tools.add(tool("get_navigation_guidance",
                "Get accurate navigation guidance for a supported Vettri workplace feature.",
                Map.of("topic", Map.of(
                        "type", "string",
                        "enum", List.of("leave", "attendance", "payslips", "devices")
                )), List.of("topic")));
        if (canUseSelf(context, "SELF_LEAVE_VIEW")) {
            tools.add(tool("get_my_leave_balance",
                    "Get the authenticated employee's current-year leave balances. No employee ID is accepted.",
                    Map.of()));
            tools.add(tool("get_my_leave_requests",
                    "Get recent leave requests for the authenticated employee. No employee ID is accepted.",
                    Map.of()));
        }
        if (canUseSelf(context, "SELF_DOCUMENT_VIEW")) {
            tools.add(tool("get_my_documents",
                    "Get the authenticated employee's document status summary. No document contents are returned.",
                    Map.of()));
        }
        if (canUseSelf(context, "SELF_PAYSLIP_VIEW")) {
            tools.add(tool("get_my_salary",
                    "Get the authenticated employee's current salary structure summary.",
                    Map.of()));
            tools.add(tool("get_my_payslip",
                    "Get the authenticated employee's recent processed payroll summaries.",
                    Map.of()));
        }
        if (allowed(context, "EMPLOYEE_MANAGE", null, null)) {
            tools.add(tool("get_pending_documents",
                    "Get pending employee document review summaries within the caller's existing employee scope.",
                    Map.of()));
        }
        if (allowed(context, "LEAVE_APPROVE", null, null)) {
            tools.add(tool("get_pending_leave_requests",
                    "Get pending leave requests within the caller's existing leave approval scope.",
                    Map.of()));
        }
        if (canViewAttendance(context, "SELF_ATTENDANCE_VIEW")) {
            tools.add(tool("get_my_attendance",
                    "Get the authenticated employee's attendance punches for a date. Omit date for today.",
                    dateSchema()));
        }
        if (canViewTeamAttendance(context)) {
            tools.add(tool("get_team_attendance",
                    "Get attendance records for employees in the caller's existing attendance scope for a date. Omit date for today. No employee or company IDs are accepted.",
                    dateSchema()));
        }
        if (canViewOfflineDevices(context)) {
            tools.add(tool("get_offline_devices",
                    "Get offline managed-device summaries in the caller's authorized organization scope.",
                    Map.of()));
        }
        return List.copyOf(tools);
    }

    public ToolResult execute(String name, Map<String, Object> arguments, AssistantContext context) {
        if (arguments == null || arguments.isEmpty() && hasArguments(name)) {
            throw new AssistantToolException(false);
        }
        return switch (name) {
            case "get_navigation_guidance" -> navigation(arguments, context);
            case "get_my_leave_balance" -> {
                requireSelf(context, "SELF_LEAVE_VIEW");
                rejectExtraArguments(arguments);
                if (context.employeeId() == null) throw new AssistantToolException(true);
                List<LeaveBalanceDTO> balances = leaveRequestService.getBalances(context.employeeId(), LocalDate.now().getYear());
                yield jsonResult(Map.of("year", LocalDate.now().getYear(), "balances",
                        balances.stream().limit(MAX_TOOL_ITEMS).map(balance -> Map.of(
                                "leaveType", safe(balance.getLeaveTypeName()),
                                "allocatedDays", balance.getAllocatedDays(),
                                "usedDays", balance.getUsedDays(),
                                "remainingDays", balance.getRemainingDays()
                        )).toList()));
            }
            case "get_my_leave_requests" -> {
                requireSelf(context, "SELF_LEAVE_VIEW");
                rejectExtraArguments(arguments);
                if (context.employeeId() == null) throw new AssistantToolException(true);
                List<LeaveRequestDTO> requests = leaveRequestService.listByEmployee(context.employeeId());
                yield jsonResult(Map.of("requests", requests.stream().limit(MAX_TOOL_ITEMS).map(request -> Map.of(
                        "leaveType", safe(request.getLeaveTypeName()),
                        "startDate", String.valueOf(request.getStartDate()),
                        "endDate", String.valueOf(request.getEndDate()),
                        "status", safe(request.getStatus()),
                        "days", request.getDays()
                )).toList()));
            }
            case "get_my_documents" -> {
                requireSelf(context, "SELF_DOCUMENT_VIEW");
                rejectExtraArguments(arguments);
                if (context.employeeId() == null) throw new AssistantToolException(true);
                List<EmployeeDocumentDTO> documents = employeeDocumentService.byEmployee(context.employeeId());
                Map<String, Long> counts = documents.stream().collect(Collectors.groupingBy(
                        document -> safe(document.getStatus()), TreeMap::new, Collectors.counting()));
                yield jsonResult(Map.of("documentCount", documents.size(), "statusCounts", counts));
            }
            case "get_my_salary" -> {
                requireSelf(context, "SELF_PAYSLIP_VIEW");
                rejectExtraArguments(arguments);
                EmployeeSalaryDetailDTO detail = employeeSalaryService.getDetail(requiredEmployee(context));
                SalaryStructureDTO current = detail.getCurrentStructure();
                if (current == null) yield jsonResult(Map.of("salaryStructure", "No active salary structure is available."));
                yield jsonResult(Map.of(
                        "effectiveFrom", String.valueOf(current.getEffectiveFrom()),
                        "grossSalary", current.getGrossSalary(),
                        "totalDeductions", current.getTotalDeductions(),
                        "netSalary", current.getNetSalary()
                ));
            }
            case "get_my_payslip" -> {
                requireSelf(context, "SELF_PAYSLIP_VIEW");
                rejectExtraArguments(arguments);
                EmployeeSalaryDetailDTO detail = employeeSalaryService.getDetail(requiredEmployee(context));
                List<Map<String, Object>> rows = detail.getPayrollHistory().stream().limit(6)
                        .map(this::safePayrollSummary).toList();
                yield jsonResult(Map.of("recentPayroll", rows));
            }
            case "get_pending_documents" -> {
                require(context, "EMPLOYEE_MANAGE", null, null);
                rejectExtraArguments(arguments);
                List<EmployeeDocumentDTO> documents = employeeDocumentService.pendingReviewInAuthorizedScope();
                yield jsonResult(Map.of("count", documents.size(), "documents", documents.stream()
                        .limit(MAX_TOOL_ITEMS)
                        .map(document -> Map.of(
                                "employeeName", safe(document.getEmployeeName()),
                                "documentType", safe(document.getDocumentType()),
                                "status", safe(document.getStatus())
                        )).toList()));
            }
            case "get_pending_leave_requests" -> {
                require(context, "LEAVE_APPROVE", null, null);
                rejectExtraArguments(arguments);
                List<LeaveRequestDTO> requests = leaveRequestService.listAll("PENDING");
                yield jsonResult(Map.of("count", requests.size(), "requests", requests.stream().limit(MAX_TOOL_ITEMS)
                        .map(request -> Map.of(
                                "employeeName", safe(request.getEmployeeName()),
                                "leaveType", safe(request.getLeaveTypeName()),
                                "startDate", String.valueOf(request.getStartDate()),
                                "endDate", String.valueOf(request.getEndDate()),
                                "days", request.getDays()
                        )).toList()));
            }
            case "get_my_attendance" -> {
                requireSelf(context, "SELF_ATTENDANCE_VIEW");
                requireOnlyDate(arguments);
                if (context.employeeId() == null) throw new AssistantToolException(true);
                LocalDate date = parseDate(arguments);
                List<AttendanceRecord> rows = attendanceRecordRepository
                        .findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndPunchTimeBetweenOrderByPunchTimeDesc(
                                context.companyId(), context.employeeId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
                yield jsonResult(attendanceRows(rows));
            }
            case "get_team_attendance" -> {
                requireTeamAttendance(context);
                requireOnlyDate(arguments);
                LocalDate date = parseDate(arguments);
                var employeeScope = authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW");
                List<AttendanceRecord> rows;
                if (employeeScope.isPresent()) {
                    if (employeeScope.get().isEmpty()) throw new AssistantToolException(true);
                    rows = attendanceRecordRepository.findScopedByCompanyAndEmployees(
                            context.companyId(), employeeScope.get(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
                } else {
                    rows = attendanceRecordRepository.findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            context.companyId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
                }
                yield jsonResult(attendanceRows(rows));
            }
            case "get_offline_devices" -> {
                requireOfflineDevices(context);
                rejectExtraArguments(arguments);
                List<MonitoredDeviceDTO> offline = deviceEnrollmentService.listAll().stream()
                        .filter(device -> !device.isOnline())
                        .toList();
                yield jsonResult(Map.of("count", offline.size(), "devices", offline.stream().limit(MAX_TOOL_ITEMS).map(device -> Map.of(
                        "deviceName", safe(device.getDeviceName()),
                        "employeeName", safe(device.getEmployeeName()),
                        "lastSeenAt", String.valueOf(device.getLastSeenAt())
                )).toList()));
            }
            default -> throw new AssistantToolException(false);
        };
    }

    private Map<String, Object> attendanceRows(List<AttendanceRecord> rows) {
        return Map.of("records", rows.stream().limit(MAX_TOOL_ITEMS).map(AttendanceRecordDTO::from)
                .map(dto -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("employeeName", safe(dto.getEmployeeName()));
                    result.put("dateTime", String.valueOf(dto.getPunchTime()));
                    result.put("punchType", safe(dto.getPunchType()));
                    result.put("status", safe(dto.getStatus()));
                    result.put("source", safe(dto.getSource()));
                    return result;
                }).toList());
    }

    private boolean hasArguments(String name) {
        return "get_my_attendance".equals(name) || "get_team_attendance".equals(name)
                || "get_navigation_guidance".equals(name);
    }

    private Long requiredEmployee(AssistantContext context) {
        if (context.employeeId() == null) throw new AssistantToolException(true);
        return context.employeeId();
    }

    private Map<String, Object> safePayrollSummary(PayrollItemDTO payroll) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentDate", String.valueOf(payroll.getPaymentDate()));
        result.put("status", safe(payroll.getStatus()));
        result.put("grossSalary", payroll.getGrossSalary());
        result.put("totalDeductions", payroll.getTotalDeductions());
        result.put("netSalary", payroll.getNetSalary());
        result.put("remarks", safe(payroll.getRemarks()));
        return result;
    }

    private LocalDate parseDate(Map<String, Object> arguments) {
        Object value = arguments.get("date");
        if (value == null) return LocalDate.now();
        if (!(value instanceof String date) || date.length() > 10) throw new AssistantToolException(false);
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (parsed.isAfter(LocalDate.now()) || parsed.isBefore(LocalDate.now().minusDays(90))) {
                throw new AssistantToolException(false);
            }
            return parsed;
        } catch (java.time.format.DateTimeParseException ex) {
            throw new AssistantToolException(false);
        }
    }

    private boolean canUseSelf(AssistantContext context, String permission) {
        return context.employeeId() != null && context.hasAuthority("SELF_PROFILE_VIEW")
                && context.hasAuthority(permission)
                && allowed(context, permission, "EMPLOYEE", context.employeeId());
    }

    private boolean canViewAttendance(AssistantContext context, String permission) {
        return context.employeeId() != null && context.hasAuthority("SELF_PROFILE_VIEW")
                && context.hasAuthority(permission)
                && allowed(context, permission, "EMPLOYEE", context.employeeId());
    }

    private boolean canViewTeamAttendance(AssistantContext context) {
        return context.hasAuthority("ATTENDANCE_VIEW") && canViewAttendanceScope(context);
    }

    private boolean canViewAttendanceScope(AssistantContext context) {
        return allowed(context, "ATTENDANCE_VIEW", null, null)
                && authorizationService.getScopes("ATTENDANCE_VIEW").stream()
                .map(Enum::name)
                .anyMatch(TEAM_SCOPE_NAMES::contains);
    }

    private boolean canViewOfflineDevices(AssistantContext context) {
        return canViewDeviceManagement(context);
    }

    public DeviceMappingGuidance deviceMappingGuidance(AssistantContext context) {
        if (canManageDevices(context)) {
            return new DeviceMappingGuidance(
                    "Open IT Management → Devices. For an unassigned device, choose Map device; for an assigned device, choose Change employee. Select the employee, review the device and employee, then choose Confirm assignment. Vettri checks the device and employee against your authorized company and scope.",
                    new com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction(
                            "NAVIGATE", "Open Devices", "/monitoring/devices"));
        }
        if (canViewDeviceManagement(context)) {
            return new DeviceMappingGuidance(
                    "You don't have permission to map devices. You can open IT Management → Devices to view devices available to you, but mapping requires device-management permission.",
                    new com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction(
                            "NAVIGATE", "Open Devices", "/monitoring/devices"));
        }
        return new DeviceMappingGuidance(
                "You don't have permission to map devices. Contact your IT team for assistance.", null);
    }

    private boolean canViewDeviceManagement(AssistantContext context) {
        return context.hasAuthority("IT_MANAGEMENT_ACCESS")
                && context.hasAuthority("MONITORING_VIEW")
                && authorizationService.isAllowed("IT_MANAGEMENT_ACCESS", null, null)
                && authorizationService.isAllowed("MONITORING_VIEW", null, null)
                && authorizationService.hasOrganizationScope("MONITORING_VIEW");
    }

    private boolean canManageDevices(AssistantContext context) {
        return canViewDeviceManagement(context)
                && context.hasAuthority("MONITORING_MANAGE")
                && authorizationService.isAllowed("MONITORING_MANAGE", null, null)
                && authorizationService.hasOrganizationScope("MONITORING_MANAGE");
    }

    private void requireOfflineDevices(AssistantContext context) {
        if (!canViewOfflineDevices(context)) throw new AssistantToolException(true);
    }

    private void requireTeamAttendance(AssistantContext context) {
        if (!canViewTeamAttendance(context)) throw new AssistantToolException(true);
    }

    private void requireSelf(AssistantContext context, String permission) {
        if (!canUseSelf(context, permission)) throw new AssistantToolException(true);
    }

    private void require(AssistantContext context, String permission, String resourceType, Long resourceId) {
        if (!context.hasAuthority(permission)
                || !authorizationService.isAllowed(permission, resourceType, resourceId)) {
            throw new AssistantToolException(true);
        }
    }

    private boolean allowed(AssistantContext context, String permission, String resourceType, Long resourceId) {
        return context.hasAuthority(permission)
                && authorizationService.isAllowed(permission, resourceType, resourceId);
    }

    private void requireOnlyDate(Map<String, Object> arguments) {
        if (arguments.keySet().stream().anyMatch(key -> !"date".equals(key))) {
            throw new AssistantToolException(false);
        }
    }

    private void rejectExtraArguments(Map<String, Object> arguments) {
        if (!arguments.isEmpty()) throw new AssistantToolException(false);
    }

    private AiProvider.ModelTool tool(String name, String description, Map<String, Object> properties) {
        List<String> required = properties.containsKey("date") ? List.of() : List.copyOf(properties.keySet());
        return tool(name, description, properties, required);
    }

    private AiProvider.ModelTool tool(
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return new AiProvider.ModelTool(name, description, schema);
    }

    private Map<String, Object> dateSchema() {
        return Map.of("date", Map.of(
                "type", "string",
                "format", "date",
                "description", "Optional ISO date, no more than 90 days in the past and not in the future."
        ));
    }

    private ToolResult jsonResult(Object data) {
        try {
            return new ToolResult(objectMapper.writeValueAsString(data), null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssistantToolException(false);
        }
    }

    private ToolResult navigation(Map<String, Object> arguments, AssistantContext context) {
        if (!arguments.keySet().equals(Set.of("topic")) || !(arguments.get("topic") instanceof String topic)) {
            throw new AssistantToolException(false);
        }
        return switch (topic) {
            case "leave" -> jsonResult(Map.of(
                    "guidance", "Open the Leave area to apply for leave or review requests.",
                    "action", "Open leave"
            ), new com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction(
                    "NAVIGATE", "Open leave",
                    context.hasAuthority("SELF_PROFILE_VIEW") && context.hasAuthority("SELF_LEAVE_VIEW")
                            ? "/my-profile?tab=leave" : "/leave"));
            case "attendance" -> jsonResult(Map.of(
                    "guidance", "Open My Attendance to review your own attendance.",
                    "action", "Open attendance"
            ), new com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction(
                    "NAVIGATE", "Open attendance",
                    context.hasAuthority("SELF_PROFILE_VIEW") && context.hasAuthority("SELF_ATTENDANCE_VIEW")
                            ? "/my-attendance" : "/attendance"));
            case "payslips" -> {
                if (!canUseSelf(context, "SELF_PAYSLIP_VIEW")) throw new AssistantToolException(true);
                yield jsonResult(Map.of(
                        "guidance", "Your payslips are available from My Pay.",
                        "action", "Open My Pay"
                ), new com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction(
                        "NAVIGATE", "Open My Pay", "/my-payslip"));
            }
            case "devices" -> {
                DeviceMappingGuidance guidance = deviceMappingGuidance(context);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("guidance", guidance.message());
                if (guidance.action() != null) result.put("action", guidance.action().label());
                yield jsonResult(result, guidance.action());
            }
            default -> throw new AssistantToolException(false);
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ToolResult jsonResult(Object data, com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action) {
        try {
            return new ToolResult(objectMapper.writeValueAsString(data), action);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssistantToolException(false);
        }
    }

    public record ToolResult(
            String sanitizedJson,
            com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action
    ) {}

    public record DeviceMappingGuidance(
            String message,
            com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action
    ) {}
}
