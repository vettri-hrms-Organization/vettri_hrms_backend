package com.haodaone.assistant;

import com.haodaone.assistant.dto.AssistantChatRequest;
import com.haodaone.assistant.dto.AssistantChatResponse;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AssistantService {
    private static final List<IntentDefinition> INTENTS = List.of(
            new IntentDefinition("LEAVE_GUIDE", "How to request leave", "GUIDE",
                    List.of("leave", "leaves", "time off", "vacation", "casual leave", "sick leave", "apply", "request")),
            new IntentDefinition("MY_LEAVE_BALANCE", "Check my leave balance", "TELL",
                    List.of("leave balance", "remaining leave", "days left", "casual leave balance", "sick leave balance")),
            new IntentDefinition("MY_DOCUMENTS", "Check my document status", "TELL",
                    List.of("my documents", "document status", "documents status", "pending documents", "document expiry")),
            new IntentDefinition("TEAM_PENDING_LEAVE", "Review pending team leave", "TELL",
                    List.of("pending leave", "team leave", "leave approvals", "leave requests", "who requested leave")),
            new IntentDefinition("ATTENDANCE_GUIDE", "Find attendance", "FIND",
                    List.of("attendance", "check in", "clock in", "hours worked", "timesheet")),
            new IntentDefinition("PAYSLIP_GUIDE", "Find my payslip", "FIND",
                    List.of("payslip", "pay slip", "salary slip", "my pay")),
            new IntentDefinition("DEVICE_GUIDE", "Find IT devices", "FIND",
                    List.of("device", "devices", "offline laptop", "offline computer", "biometric")),
            new IntentDefinition("UNKNOWN", "Unknown request", "GUIDE", List.of())
    );

    private final AssistantLanguageProvider languageProvider;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestService leaveRequestService;
    private final EmployeeDocumentService employeeDocumentService;

    public AssistantService(
            AssistantLanguageProvider languageProvider,
            EmployeeRepository employeeRepository,
            LeaveRequestService leaveRequestService,
            EmployeeDocumentService employeeDocumentService
    ) {
        this.languageProvider = languageProvider;
        this.employeeRepository = employeeRepository;
        this.leaveRequestService = leaveRequestService;
        this.employeeDocumentService = employeeDocumentService;
    }

    @Transactional(readOnly = true)
    public AssistantChatResponse chat(AssistantChatRequest request) {
        AssistantContext context = authenticatedContext();
        List<IntentDefinition> availableIntents = INTENTS.stream()
                .filter(intent -> isIntentAvailable(intent, context))
                .filter(intent -> !"UNKNOWN".equals(intent.id()))
                .toList();
        List<String> allowedIds = availableIntents.stream().map(IntentDefinition::id).toList();

        Optional<String> providerIntent = languageProvider.classify(
                request.message().trim(),
                request.history(),
                allowedIds
        );
        IntentDefinition intent = providerIntent
                .flatMap(id -> availableIntents.stream().filter(candidate -> candidate.id().equals(id)).findFirst())
                .orElseGet(() -> matchLocally(request.message(), request.history(), availableIntents));

        return respond(intent, context, providerIntent.isPresent());
    }

    private AssistantContext authenticatedContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new AccessDeniedException("Authentication required");
        }

        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new AccessDeniedException("Workspace context required");

        Set<String> authorities = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }

        Long employeeId = employeeRepository.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(employee -> employee.getCompany() != null
                        && companyId.equals(employee.getCompany().getId()))
                .map(Employee::getId)
                .orElse(null);

        return new AssistantContext(
                principal.getId(),
                employeeId,
                companyId,
                Set.copyOf(authorities),
                Set.copyOf(principal.getRoleNames())
        );
    }

    private boolean isIntentAvailable(IntentDefinition intent, AssistantContext context) {
        if ("LEAVE_GUIDE".equals(intent.id())) {
            return (context.hasAuthority("SELF_PROFILE_VIEW") && context.hasAuthority("SELF_LEAVE_VIEW"))
                    || context.hasAuthority("LEAVE_VIEW");
        }
        if ("MY_LEAVE_BALANCE".equals(intent.id())) {
            return context.employeeId() != null
                    && context.hasAuthority("SELF_PROFILE_VIEW")
                    && context.hasAuthority("SELF_LEAVE_VIEW");
        }
        if ("MY_DOCUMENTS".equals(intent.id())) {
            return context.employeeId() != null
                    && context.hasAuthority("SELF_PROFILE_VIEW")
                    && context.hasAuthority("SELF_DOCUMENT_VIEW");
        }
        if ("TEAM_PENDING_LEAVE".equals(intent.id())) {
            return context.hasAuthority("LEAVE_APPROVE");
        }
        if ("ATTENDANCE_GUIDE".equals(intent.id())) {
            return (context.hasAuthority("SELF_PROFILE_VIEW") && context.hasAuthority("SELF_ATTENDANCE_VIEW"))
                    || context.hasAuthority("ATTENDANCE_VIEW");
        }
        if ("PAYSLIP_GUIDE".equals(intent.id())) {
            return context.hasAuthority("SELF_PAYSLIP_VIEW");
        }
        if ("DEVICE_GUIDE".equals(intent.id())) {
            return context.hasAuthority("IT_MANAGEMENT_ACCESS")
                    && context.hasAuthority("MONITORING_VIEW");
        }
        return false;
    }

    private IntentDefinition matchLocally(
            String message,
            List<AssistantChatRequest.ConversationTurn> history,
            List<IntentDefinition> availableIntents
    ) {
        String normalized = normalize(message);
        if (isFollowUp(normalized) && history != null) {
            String previousContext = history.stream()
                    .map(AssistantChatRequest.ConversationTurn::content)
                    .map(this::normalize)
                    .reduce("", (first, second) -> first + " " + second);
            Optional<IntentDefinition> followUpIntent = availableIntents.stream()
                    .filter(intent -> intentMatchesContext(intent.id(), previousContext))
                    .findFirst();
            if (followUpIntent.isPresent()) return followUpIntent.get();
        }
        return availableIntents.stream()
                .map(intent -> new ScoredIntent(intent, score(intent, normalized)))
                .filter(candidate -> candidate.score() > 0)
                .max(Comparator.comparingInt(ScoredIntent::score))
                .map(ScoredIntent::intent)
                .orElse(INTENTS.get(INTENTS.size() - 1));
    }

    private boolean isFollowUp(String normalized) {
        return normalized.matches("^(why|how|when|can i|what about|and|it|that|them|those|more)(\\s+.*)?$")
                && normalized.split(" ").length <= 6;
    }

    private boolean intentMatchesContext(String intentId, String context) {
        return switch (intentId) {
            case "MY_LEAVE_BALANCE" -> context.contains("leave balance") || context.contains("days left");
            case "MY_DOCUMENTS" -> context.contains("my documents") || context.contains("document status");
            case "TEAM_PENDING_LEAVE" -> context.contains("pending leave") || context.contains("leave approvals");
            case "ATTENDANCE_GUIDE" -> context.contains("attendance") || context.contains("check in");
            case "PAYSLIP_GUIDE" -> context.contains("payslip") || context.contains("pay slip");
            case "DEVICE_GUIDE" -> context.contains("device") || context.contains("offline laptop");
            default -> false;
        };
    }

    private int score(IntentDefinition intent, String normalizedMessage) {
        if (normalizedMessage.isBlank()) return 0;
        int score = 0;
        for (String phrase : intent.phrases()) {
            String normalizedPhrase = normalize(phrase);
            if (normalizedMessage.contains(normalizedPhrase)) {
                score += 20 + normalizedPhrase.split(" ").length * 5;
            }
        }

        if ("LEAVE_GUIDE".equals(intent.id())
                && (normalizedMessage.contains("apply") || normalizedMessage.contains("request")
                || normalizedMessage.contains("tomorrow") || normalizedMessage.contains("help me"))) {
            score += 20;
        }
        if ("TEAM_PENDING_LEAVE".equals(intent.id())
                && (normalizedMessage.contains("team")
                || normalizedMessage.contains("pending")
                || normalizedMessage.contains("approval"))) {
            score += 25;
        }
        if ("MY_LEAVE_BALANCE".equals(intent.id()) && normalizedMessage.contains("my")) score += 10;
        return score;
    }

    private AssistantChatResponse respond(IntentDefinition intent, AssistantContext context, boolean aiEnhanced) {
        return switch (intent.id()) {
            case "LEAVE_GUIDE" -> navigationResponse(
                    "LEAVE_GUIDE",
                    "GUIDE",
                    "To request leave, open your leave area, choose the leave type and dates, add any required details, then submit the request for review.",
                    canNavigate(context, "SELF_PROFILE_VIEW") && canNavigate(context, "SELF_LEAVE_VIEW")
                            ? "/my-profile?tab=leave"
                            : "/leave",
                    "Open leave",
                    aiEnhanced
            );
            case "MY_LEAVE_BALANCE" -> myLeaveBalance(context, aiEnhanced);
            case "MY_DOCUMENTS" -> myDocuments(context, aiEnhanced);
            case "TEAM_PENDING_LEAVE" -> pendingTeamLeave(aiEnhanced);
            case "ATTENDANCE_GUIDE" -> navigationResponse(
                    "ATTENDANCE_GUIDE",
                    "FIND",
                    "You can review your attendance in My Attendance. Authorized attendance managers can use Attendance Management.",
                    canNavigate(context, "SELF_PROFILE_VIEW") && canNavigate(context, "SELF_ATTENDANCE_VIEW")
                            ? "/my-attendance"
                            : "/attendance",
                    "Open attendance",
                    aiEnhanced
            );
            case "PAYSLIP_GUIDE" -> navigationResponse(
                    "PAYSLIP_GUIDE",
                    "FIND",
                    "Your payslips are available from My Pay.",
                    "/my-payslip",
                    "Open My Pay",
                    aiEnhanced
            );
            case "DEVICE_GUIDE" -> navigationResponse(
                    "DEVICE_GUIDE",
                    "FIND",
                    "Device status is available in IT Management under Devices. This assistant does not inspect device telemetry yet.",
                    "/monitoring/devices",
                    "Open Devices",
                    aiEnhanced
            );
            default -> new AssistantChatResponse(
                    "I couldn't find a reliable answer for that in Vettri yet. Try asking about requesting leave, your leave balance, your documents, attendance, payslips, or an authorized IT page.",
                    "UNKNOWN",
                    "GUIDE",
                    List.of(),
                    List.of("How do I request leave?", "What is my leave balance?"),
                    false,
                    aiEnhanced
            );
        };
    }

    private AssistantChatResponse myLeaveBalance(AssistantContext context, boolean aiEnhanced) {
        List<LeaveBalanceDTO> balances = leaveRequestService.getBalances(context.employeeId(), LocalDate.now().getYear());
        if (balances.isEmpty()) {
            return new AssistantChatResponse(
                    "There are no active leave types configured for this workspace.",
                    "MY_LEAVE_BALANCE",
                    "TELL",
                    List.of(navigate("/my-profile?tab=leave", "Open leave")),
                    List.of(),
                    false,
                    aiEnhanced
            );
        }

        String balanceText = balances.stream()
                .map(balance -> String.format(
                        Locale.ROOT,
                        "%s: %.1f days remaining",
                        balance.getLeaveTypeName(),
                        balance.getRemainingDays()
                ))
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
        return new AssistantChatResponse(
                "Your leave balance for " + LocalDate.now().getYear() + ":\n" + balanceText,
                "MY_LEAVE_BALANCE",
                "TELL",
                List.of(navigate("/my-profile?tab=leave", "View leave")),
                List.of(),
                false,
                aiEnhanced
        );
    }

    private AssistantChatResponse myDocuments(AssistantContext context, boolean aiEnhanced) {
        List<EmployeeDocumentDTO> documents = employeeDocumentService.byEmployee(context.employeeId());
        String message;
        if (documents.isEmpty()) {
            message = "There are no employee documents on file yet.";
        } else {
            LocalDate today = LocalDate.now();
            long pending = documents.stream().filter(document -> "PENDING".equalsIgnoreCase(document.getStatus())).count();
            long rejected = documents.stream().filter(document -> "REJECTED".equalsIgnoreCase(document.getStatus())).count();
            long expired = documents.stream()
                    .filter(document -> document.getExpiryDate() != null && document.getExpiryDate().isBefore(today))
                    .count();
            StringBuilder summary = new StringBuilder("You have ")
                    .append(documents.size()).append(" document")
                    .append(documents.size() == 1 ? "" : "s").append(" on file.");
            if (pending > 0) summary.append("\n").append(pending).append(" pending review.");
            if (rejected > 0) summary.append("\n").append(rejected).append(" need")
                    .append(rejected == 1 ? "s" : "").append(" attention after review.");
            if (expired > 0) summary.append("\n").append(expired).append(" expired.");
            message = summary.toString();
        }
        return new AssistantChatResponse(
                message,
                "MY_DOCUMENTS",
                "TELL",
                List.of(navigate("/my-profile?tab=documents", "View my documents")),
                List.of(),
                false,
                aiEnhanced
        );
    }

    private AssistantChatResponse pendingTeamLeave(boolean aiEnhanced) {
        List<LeaveRequestDTO> pendingRequests = leaveRequestService.listAll("PENDING");
        String message;
        if (pendingRequests.isEmpty()) {
            message = "There are no pending leave requests in the employees visible to your existing leave permissions and scope.";
        } else {
            String summary = pendingRequests.stream()
                    .limit(5)
                    .map(request -> request.getEmployeeName() + " — " + request.getLeaveTypeName()
                            + ", " + request.getStartDate() + " to " + request.getEndDate())
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("");
            message = "Pending leave requests in your authorized scope (" + pendingRequests.size() + "):\n" + summary;
            if (pendingRequests.size() > 5) message += "\nAnd " + (pendingRequests.size() - 5) + " more.";
        }
        return new AssistantChatResponse(
                message,
                "TEAM_PENDING_LEAVE",
                "TELL",
                List.of(navigate("/leave", "Review leave requests")),
                List.of(),
                false,
                aiEnhanced
        );
    }

    private AssistantChatResponse navigationResponse(
            String intent,
            String mode,
            String message,
            String route,
            String actionLabel,
            boolean aiEnhanced
    ) {
        return new AssistantChatResponse(
                message,
                intent,
                mode,
                List.of(navigate(route, actionLabel)),
                List.of(),
                false,
                aiEnhanced
        );
    }

    private boolean canNavigate(AssistantContext context, String permission) {
        return context.hasAuthority(permission);
    }

    private AssistantChatResponse.AssistantAction navigate(String route, String label) {
        return new AssistantChatResponse.AssistantAction("NAVIGATE", label, route);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record IntentDefinition(
            String id,
            String label,
            String mode,
            List<String> phrases
    ) {}

    private record ScoredIntent(IntentDefinition intent, int score) {}

    private record AssistantContext(
            Long userId,
            Long employeeId,
            Long companyId,
            Set<String> authorities,
            Set<String> roleNames
    ) {
        boolean hasAuthority(String authority) {
            return authorities.contains(authority) || authorities.contains("ROLE_" + authority);
        }

        boolean hasAnyAuthority(String... requested) {
            for (String authority : requested) {
                if (hasAuthority(authority)) return true;
            }
            return false;
        }
    }
}
