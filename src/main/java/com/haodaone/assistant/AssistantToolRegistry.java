package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.AttendanceRecordDTO;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.assistant.dto.AssistantChatResponse;
import com.haodaone.config.ApplicationTimeConfig;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.salary.dto.EmployeeSalaryDetailDTO;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.service.EmployeeSalaryService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AssistantToolRegistry {
    private static final int MAX_TOOL_ITEMS = 50;
    private static final List<String> TEAM_SCOPE_NAMES = List.of("TEAM", "DEPARTMENT", "ORGANIZATION");
    private static final Pattern LEAVE_TYPE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:cl|el|sl)\\b|\\b(?!(?:apply|request|book|take|submit|put|for|on|from|to|my|the|a|an|some|any|this|that)\\b)"
                    + "[a-z][a-z-]*\\s+leaves?\\b");

    private final ObjectMapper objectMapper;
    private final LeaveRequestService leaveRequestService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AssistantConversationStore conversationStore;
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
            AuthorizationService authorizationService,
            LeaveTypeRepository leaveTypeRepository,
            AssistantConversationStore conversationStore
    ) {
        this.objectMapper = objectMapper;
        this.leaveRequestService = leaveRequestService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.conversationStore = conversationStore;
        this.employeeDocumentService = employeeDocumentService;
        this.deviceEnrollmentService = deviceEnrollmentService;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeSalaryService = employeeSalaryService;
        this.authorizationService = authorizationService;
    }

    public List<AiProvider.ModelTool> availableTools(AssistantContext context) {
        return availableTools(context, true);
    }

    public List<AiProvider.ModelTool> availableTools(AssistantContext context, boolean includeLeavePreparation) {
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
        if (includeLeavePreparation && canApplyOwnLeave(context)) {
            tools.add(tool("prepare_leave_request",
                    "Prepare and validate a leave request for the authenticated employee. This is read-only and never submits. Call only when the user explicitly asks to apply or change their own leave. It accepts no arguments; the backend extracts dates and resolves the leave type from the user's message and current company configuration.",
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
        return execute(name, arguments, context, null, null, null);
    }

    public ToolResult execute(
            String name,
            Map<String, Object> arguments,
            AssistantContext context,
            java.util.UUID conversationId,
            String userMessage,
            AssistantConversationStore.PendingLeaveAction priorPending
    ) {
        if (arguments == null || arguments.isEmpty() && hasArguments(name)) {
            throw new AssistantToolException(false);
        }
        return switch (name) {
            case "get_navigation_guidance" -> navigation(arguments, context);
            case "prepare_leave_request" -> {
                rejectExtraArguments(arguments);
                yield prepareLeaveRequest(conversationId, userMessage, context, priorPending);
            }
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

    public ToolResult prepareLeaveRequest(
            java.util.UUID conversationId,
            String userMessage,
            AssistantContext context,
            AssistantConversationStore.PendingLeaveAction priorPending
    ) {
        if (!canApplyOwnLeave(context)) {
            return new ToolResult(
                    "{\"error\":\"forbidden\"}",
                    "You don't have permission to apply for leave for yourself.",
                    null
            );
        }
        if (conversationId == null || userMessage == null || context.employeeId() == null) {
            return new ToolResult(
                    "{\"error\":\"request_unavailable\"}",
                    "I couldn't prepare that leave request. Please try again.",
                    null
            );
        }

        String normalizedMessage = normalize(userMessage);
        if (normalizedMessage.matches(".*\\b(half day|half-day|morning|afternoon)\\b.*")) {
            return new ToolResult(
                    "{\"error\":\"duration_unsupported\"}",
                    "The current Vettri leave application supports full-day leave only. I haven't prepared or submitted a request.",
                    null
            );
        }

        List<LeaveType> activeTypes = leaveTypeRepository
                .findAllByCompany_IdAndDeletedFalseOrderByNameAsc(context.companyId()).stream()
                .filter(LeaveType::isActive)
                .toList();
        LeaveType leaveType = resolveLeaveType(normalizedMessage, activeTypes);
        if (leaveType == null && priorPending != null && priorPending.leaveTypeId() != null
                && !mentionsLeaveType(normalizedMessage)) {
            leaveType = activeTypes.stream()
                    .filter(type -> type.getId().equals(priorPending.leaveTypeId()))
                    .findFirst()
                    .orElse(null);
        }

        AssistantLeaveDateParser.DateRange dateRange;
        try {
            dateRange = AssistantLeaveDateParser.parse(
                    userMessage, LocalDate.now(ApplicationTimeConfig.APPLICATION_ZONE));
        } catch (AssistantLeaveDateParser.AmbiguousDateException ex) {
            return new ToolResult(
                    "{\"error\":\"ambiguous_date\"}",
                    "I couldn't resolve that date unambiguously. Please provide the day, month, and year. I haven't submitted anything.",
                    null
            );
        }
        if (dateRange == null && priorPending != null
                && priorPending.startDate() != null && priorPending.endDate() != null) {
            dateRange = new AssistantLeaveDateParser.DateRange(priorPending.startDate(), priorPending.endDate());
        }

        String reason = extractReason(userMessage).orElse(
                priorPending == null ? null : priorPending.reason());
        if (reason != null && reason.length() > 500) {
            return new ToolResult(
                    "{\"error\":\"reason_too_long\"}",
                    "Please shorten the reason to 500 characters or fewer. I haven't submitted anything.",
                    null
            );
        }

        if (leaveType == null) {
            AssistantConversationStore.PendingLeaveAction draft = new AssistantConversationStore.PendingLeaveAction(
                    context.employeeId(), null, null,
                    dateRange == null ? null : dateRange.startDate(),
                    dateRange == null ? null : dateRange.endDate(),
                    reason, null, null, false, LocalDateTime.now()
            );
            conversationStore.savePendingLeave(conversationId, context.companyId(), context.userId(), draft);
            String typePrompt = activeTypes.isEmpty()
                    ? "I couldn't find an active leave type available for your account. Please contact your HR team."
                    : (mentionsLeaveType(normalizedMessage)
                            ? "I couldn't find that leave type available for your account."
                            : "Which leave type would you like to use?")
                            + " Available leave types: "
                            + activeTypes.stream().map(LeaveType::getName).sorted().collect(Collectors.joining(", "))
                            + ".";
            return new ToolResult("{\"missing\":\"leaveType\"}", typePrompt, null);
        }

        if (dateRange == null) {
            AssistantConversationStore.PendingLeaveAction draft = new AssistantConversationStore.PendingLeaveAction(
                    context.employeeId(), leaveType.getId(), leaveType.getName(),
                    null, null, reason, null, null, false, LocalDateTime.now()
            );
            conversationStore.savePendingLeave(conversationId, context.companyId(), context.userId(), draft);
            return new ToolResult("{\"missing\":\"date\"}", "Which date or date range would you like to take leave?", null);
        }
        if (dateRange.startDate().getYear() != dateRange.endDate().getYear()) {
            return new ToolResult(
                    "{\"error\":\"cross_year_range\"}",
                    "I couldn't verify a leave request spanning different calendar years. Please submit separate requests for each year.",
                    null
            );
        }

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(context.employeeId());
        request.setLeaveTypeId(leaveType.getId());
        request.setStartDate(dateRange.startDate());
        request.setEndDate(dateRange.endDate());
        request.setReason(reason);
        LeaveRequestService.LeaveApplicationPreview preview;
        try {
            preview = leaveRequestService.preview(request);
        } catch (BadRequestException ex) {
            return new ToolResult(
                    "{\"error\":\"leave_validation_failed\"}",
                    safeValidationMessage(ex.getMessage()),
                    null
            );
        } catch (AccessDeniedException ex) {
            return new ToolResult(
                    "{\"error\":\"forbidden\"}",
                    "You don't have permission to apply for leave for yourself.",
                    null
            );
        }

        AssistantConversationStore.PendingLeaveAction prepared = new AssistantConversationStore.PendingLeaveAction(
                context.employeeId(), preview.leaveTypeId(), preview.leaveTypeName(),
                preview.startDate(), preview.endDate(), reason,
                preview.requestedDays(), preview.remainingDays(), true, LocalDateTime.now()
        );
        conversationStore.savePendingLeave(conversationId, context.companyId(), context.userId(), prepared);
        String period = formatPeriod(preview.startDate(), preview.endDate());
        String days = formatDays(preview.requestedDays());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("leaveType", preview.leaveTypeName());
        details.put("startDate", preview.startDate().toString());
        details.put("endDate", preview.endDate().toString());
        details.put("days", preview.requestedDays());
        details.put("remainingDays", preview.remainingDays());
        details.put("duration", "Full day");
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason);
        }
        AssistantChatResponse.AssistantAction action = new AssistantChatResponse.AssistantAction(
                "LEAVE_CONFIRMATION", "Submit Leave", null, details);
        return new ToolResult(
                "{\"prepared\":true,\"leaveType\":\"" + safeJson(preview.leaveTypeName())
                        + "\",\"startDate\":\"" + preview.startDate()
                        + "\",\"endDate\":\"" + preview.endDate()
                        + "\",\"days\":" + preview.requestedDays()
                        + ",\"remainingDays\":" + preview.remainingDays() + "}",
                "I've prepared your leave request:\n\n"
                        + preview.leaveTypeName() + "\n"
                        + period + "\n"
                        + days + (preview.requestedDays() == 1 ? " day" : " days")
                        + "\nAvailable balance: " + formatDays(preview.remainingDays())
                        + (preview.remainingDays() == 1 ? " day." : " days.")
                        + "\n\nWould you like me to submit it?",
                action
        );
    }

    @Transactional
    public LeaveRequestDTO submitConfirmedLeave(
            java.util.UUID conversationId,
            AssistantContext context
    ) {
        if (!canApplyOwnLeave(context) || context.employeeId() == null) {
            throw new AssistantToolException(true);
        }
        AssistantConversationStore.PendingLeaveAction pending = conversationStore
                .lockPendingLeave(conversationId, context.companyId(), context.userId())
                .filter(AssistantConversationStore.PendingLeaveAction::ready)
                .filter(action -> action.employeeId() == context.employeeId())
                .orElseThrow(() -> new AssistantToolException(false));
        if (PendingLeaveExpiry.isExpired(pending.createdAt(), LocalDateTime.now())) {
            throw new PendingLeaveExpiredException();
        }

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(context.employeeId());
        request.setLeaveTypeId(pending.leaveTypeId());
        request.setStartDate(pending.startDate());
        request.setEndDate(pending.endDate());
        request.setReason(pending.reason());
        LeaveRequestDTO created = leaveRequestService.apply(request);
        conversationStore.clearPendingLeave(conversationId, context.companyId(), context.userId());
        return created;
    }

    public void cancelPendingLeave(java.util.UUID conversationId, AssistantContext context) {
        conversationStore.clearPendingLeave(conversationId, context.companyId(), context.userId());
    }

    private boolean canApplyOwnLeave(AssistantContext context) {
        if (context.employeeId() == null) return false;
        boolean selfPermission = context.hasAuthority("SELF_LEAVE_APPLY")
                && authorizationService.isAllowed("SELF_LEAVE_APPLY", "EMPLOYEE", context.employeeId());
        boolean delegatedPermission = context.hasAuthority("LEAVE_APPLY")
                && authorizationService.isAllowed("LEAVE_APPLY", "EMPLOYEE", context.employeeId());
        return selfPermission || delegatedPermission;
    }

    private LeaveType resolveLeaveType(String message, List<LeaveType> activeTypes) {
        return activeTypes.stream()
                .filter(type -> containsNormalized(message, type.getName())
                        || containsNormalized(message, type.getCode()))
                .sorted(Comparator.comparingInt((LeaveType type) -> normalized(type.getName()).length()).reversed())
                .findFirst()
                .orElse(null);
    }

    private boolean mentionsLeaveType(String message) {
        return LEAVE_TYPE_REFERENCE.matcher(message).find();
    }

    private boolean containsNormalized(String message, String candidate) {
        String normalizedCandidate = normalized(candidate);
        return !normalizedCandidate.isBlank()
                && (" " + message + " ").contains(" " + normalizedCandidate + " ");
    }

    private String normalize(String value) {
        return " " + value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\bleaves\\b", "leave")
                .strip() + " ";
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private Optional<String> extractReason(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)\\b(?:because|due to|reason(?: is)?)\\s+(.+)$").matcher(message);
        if (!matcher.find()) return Optional.empty();
        String reason = matcher.group(1).strip().replaceAll("[.!?]+$", "");
        return reason.isBlank() ? Optional.empty() : Optional.of(reason);
    }

    private String safeValidationMessage(String message) {
        if (message == null || message.isBlank()) {
            return "I couldn't validate that request against Vettri's current leave rules. I haven't submitted anything.";
        }
        return "I couldn't prepare that leave request: " + message + ". I haven't submitted anything.";
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
        String start = startDate.format(formatter);
        return startDate.equals(endDate) ? start : start + " to " + endDate.format(formatter);
    }

    private String formatDays(double days) {
        return days == Math.rint(days) ? String.format(Locale.ROOT, "%.0f", days)
                : String.format(Locale.ROOT, "%.1f", days);
    }

    private String safeJson(String value) {
        try {
            return objectMapper.writeValueAsString(value).replaceAll("^\"|\"$", "");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AssistantToolException(false);
        }
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
            String message,
            com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action
    ) {
        public ToolResult(
                String sanitizedJson,
                com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action
        ) {
            this(sanitizedJson, null, action);
        }
    }

    public record DeviceMappingGuidance(
            String message,
            com.haodaone.assistant.dto.AssistantChatResponse.AssistantAction action
    ) {}
}
