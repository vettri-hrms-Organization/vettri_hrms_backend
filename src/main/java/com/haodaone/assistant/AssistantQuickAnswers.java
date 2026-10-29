package com.haodaone.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fast path for Ask Vettri: everyday questions ("my leave balance", "my last payslip",
 * "pending approvals") are routed straight to the matching backend tool and answered
 * from its data, without waiting for the language model. Tool answers chosen by the
 * model are also written here when a formatter exists, saving the second model call.
 *
 * Nothing here bypasses security: routing only picks tools the caller already has
 * (AssistantToolRegistry.availableTools), and the tools enforce permissions themselves.
 */
@Component
public class AssistantQuickAnswers {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** Guidance questions ("how do I…", "where is…") go to the model; "how many / how much" are data questions. */
    private static final Pattern HOW_TO = Pattern.compile(
            "(?i)^\\s*(how\\s+(do|does|can|could|should|to|is|are|will)|where|why|can i|could i|should i)\\b.*");
    private static final Pattern GREETING = Pattern.compile(
            "(?i)^\\s*(hi|hii+|hello|hey|hai|good\\s+(morning|afternoon|evening)|namaste|vanakkam)[\\s!.,]*(vettri|bot|there)?[\\s!.]*$");
    private static final Pattern HELP = Pattern.compile(
            "(?i)^\\s*(help|menu|what can you do\\??|what do you do\\??|how can you help( me)?\\??)\\s*$");
    private static final Pattern BALANCE = Pattern.compile(
            "(?i).*\\b(leave\\s+balance|balance|remaining\\s+leaves?|leaves?\\s+(left|remaining|available)|how many\\s+(leaves?|days)\\b.*\\b(left|remaining|have|available))\\b.*");
    private static final Pattern MY_LEAVES = Pattern.compile(
            "(?i).*\\b(my\\s+leave\\s+(requests?|applications?|status|history)|status\\s+of\\s+my\\s+leaves?|(is|was)\\s+my\\s+leave\\s+approved)\\b.*");
    private static final Pattern PAYSLIP = Pattern.compile(
            "(?i).*\\b(pay\\s?slips?|salary\\s+slips?|net\\s+pay|take[- ]home|salary\\s+(credited|paid)|last\\s+(month'?s?\\s+)?salary)\\b.*");
    private static final Pattern SALARY = Pattern.compile(
            "(?i).*\\b(my\\s+(salary|ctc|gross)|salary\\s+structure|what\\s+is\\s+my\\s+salary)\\b.*");
    private static final Pattern MY_ATTENDANCE = Pattern.compile(
            "(?i).*\\b(my\\s+attendance|(did|have)\\s+i\\s+(punch|check)(ed)?\\s*-?\\s*in|my\\s+(check[- ]?in|punch)(es)?|what\\s+time\\s+did\\s+i\\s+(come|check|punch))\\b.*");
    private static final Pattern TEAM_ATTENDANCE = Pattern.compile(
            "(?i).*\\b(team\\s+attendance|who\\s+(is|are)\\s+(present|absent|in(\\s+office)?|late)|attendance\\s+of\\s+(my\\s+)?team)\\b.*");
    private static final Pattern PENDING_LEAVES = Pattern.compile(
            "(?i).*\\b(pending\\s+(leave\\s+)?(approvals?|requests?)|leave\\s+requests?\\s+(to|for)\\s+approv|(waiting|awaiting)\\s+(for\\s+)?(my\\s+)?approval)\\b.*");
    private static final Pattern PENDING_DOCUMENTS = Pattern.compile(
            "(?i).*\\b(pending\\s+documents?|documents?\\s+(to|for)\\s+review|documents?\\s+awaiting)\\b.*");
    private static final Pattern MY_DOCUMENTS = Pattern.compile("(?i).*\\b(my\\s+documents?|document\\s+status)\\b.*");
    private static final Pattern OFFLINE_DEVICES = Pattern.compile(
            "(?i).*\\b(offline\\s+(devices?|laptops?|systems?)|(devices?|laptops?)\\s+(are\\s+)?offline)\\b.*");
    private static final Pattern YESTERDAY = Pattern.compile("(?i).*\\byesterday\\b.*");

    private final ObjectMapper objectMapper;

    public AssistantQuickAnswers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** A tool that can answer this message on its own, with its arguments. */
    public record Route(String tool, java.util.Map<String, Object> arguments) {}

    public boolean isGreetingOrHelp(String message) {
        return message != null && (GREETING.matcher(message).matches() || HELP.matcher(message).matches());
    }

    /** Capabilities list built from the tools this user can actually use. */
    public String help(String firstName, Set<String> tools) {
        List<String> lines = new ArrayList<>();
        if (tools.contains("get_my_leave_balance")) lines.add("• Your leave balance and leave request status");
        if (tools.contains("prepare_leave_request")) lines.add("• Applying for leave - e.g. \"Apply casual leave tomorrow\"");
        if (tools.contains("get_my_attendance")) lines.add("• Your attendance and check-in times");
        if (tools.contains("get_my_payslip")) lines.add("• Your payslips and salary summary");
        if (tools.contains("get_my_documents")) lines.add("• Your document status");
        if (tools.contains("get_pending_leave_requests")) lines.add("• Leave requests waiting for your approval");
        if (tools.contains("get_team_attendance")) lines.add("• Your team's attendance today");
        if (tools.contains("get_pending_documents")) lines.add("• Employee documents waiting for review");
        if (tools.contains("get_offline_devices")) lines.add("• Company laptops that are offline");
        lines.add("• Finding your way around Vettri");
        String hello = firstName == null || firstName.isBlank() ? "Hi!" : "Hi " + firstName + "!";
        return hello + " I'm Vettri Bot. I can help you with:\n" + String.join("\n", lines)
                + "\n\nJust ask in your own words.";
    }

    /** Picks one tool for a clear, everyday question; empty when the model should decide. */
    public Optional<Route> route(String message, Set<String> tools) {
        if (message == null || message.length() > 200 || HOW_TO.matcher(message).matches()) return Optional.empty();
        String today = LocalDate.now(IST).toString();
        String day = YESTERDAY.matcher(message).matches() ? LocalDate.now(IST).minusDays(1).toString() : today;
        if (PENDING_LEAVES.matcher(message).matches() && tools.contains("get_pending_leave_requests"))
            return Optional.of(new Route("get_pending_leave_requests", java.util.Map.of()));
        if (PENDING_DOCUMENTS.matcher(message).matches() && tools.contains("get_pending_documents"))
            return Optional.of(new Route("get_pending_documents", java.util.Map.of()));
        if (TEAM_ATTENDANCE.matcher(message).matches() && tools.contains("get_team_attendance"))
            return Optional.of(new Route("get_team_attendance", java.util.Map.of("date", day)));
        if (OFFLINE_DEVICES.matcher(message).matches() && tools.contains("get_offline_devices"))
            return Optional.of(new Route("get_offline_devices", java.util.Map.of()));
        if (MY_LEAVES.matcher(message).matches() && tools.contains("get_my_leave_requests"))
            return Optional.of(new Route("get_my_leave_requests", java.util.Map.of()));
        if (BALANCE.matcher(message).matches() && tools.contains("get_my_leave_balance"))
            return Optional.of(new Route("get_my_leave_balance", java.util.Map.of()));
        if (PAYSLIP.matcher(message).matches() && tools.contains("get_my_payslip"))
            return Optional.of(new Route("get_my_payslip", java.util.Map.of()));
        if (SALARY.matcher(message).matches() && tools.contains("get_my_salary"))
            return Optional.of(new Route("get_my_salary", java.util.Map.of()));
        if (MY_ATTENDANCE.matcher(message).matches() && tools.contains("get_my_attendance"))
            return Optional.of(new Route("get_my_attendance", java.util.Map.of("date", day)));
        if (MY_DOCUMENTS.matcher(message).matches() && tools.contains("get_my_documents"))
            return Optional.of(new Route("get_my_documents", java.util.Map.of()));
        return Optional.empty();
    }

    /** Plain-language answer from a tool's sanitized JSON; empty when there is no formatter for it. */
    public Optional<String> format(String tool, String json, String userMessage) {
        try {
            JsonNode data = objectMapper.readTree(json);
            return Optional.ofNullable(switch (tool) {
                case "get_my_leave_balance" -> leaveBalance(data, userMessage);
                case "get_my_leave_requests" -> myLeaveRequests(data);
                case "get_pending_leave_requests" -> pendingLeave(data);
                case "get_my_payslip" -> payslips(data);
                case "get_my_salary" -> salary(data);
                case "get_my_attendance" -> myAttendance(data);
                case "get_team_attendance" -> teamAttendance(data);
                case "get_my_documents" -> myDocuments(data);
                case "get_pending_documents" -> pendingDocuments(data);
                case "get_offline_devices" -> offlineDevices(data);
                default -> null;
            });
        } catch (Exception ex) {
            return Optional.empty(); // unexpected shape: let the model phrase it
        }
    }

    // ------------------------------------------------------------------ formatters

    private String leaveBalance(JsonNode data, String userMessage) {
        JsonNode balances = data.path("balances");
        if (!balances.isArray()) return null;
        if (balances.isEmpty()) return "No leave types are set up for you yet. Please check with HR.";
        String asked = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        for (JsonNode b : balances) {
            String type = b.path("leaveType").asText("");
            String firstWord = type.toLowerCase(Locale.ROOT).split(" ")[0];
            if (!firstWord.isBlank() && !"leave".equals(firstWord) && asked.contains(firstWord)) {
                return "You have **" + days(b.path("remainingDays")) + "** of " + type + " left this year ("
                        + days(b.path("usedDays")) + " used of " + days(b.path("allocatedDays")) + ").";
            }
        }
        StringBuilder text = new StringBuilder("Here's your leave balance for " + data.path("year").asText("this year") + ":\n");
        for (JsonNode b : balances) {
            text.append("• ").append(b.path("leaveType").asText("Leave")).append(": **")
                    .append(days(b.path("remainingDays"))).append("** left (")
                    .append(days(b.path("usedDays"))).append(" used of ").append(days(b.path("allocatedDays"))).append(")\n");
        }
        return text.toString().strip();
    }

    private String myLeaveRequests(JsonNode data) {
        JsonNode requests = data.path("requests");
        if (!requests.isArray()) return null;
        if (requests.isEmpty()) return "You haven't applied for any leave yet.";
        StringBuilder text = new StringBuilder("Your recent leave requests:\n");
        int shown = 0;
        for (JsonNode r : requests) {
            if (shown++ == 5) break;
            text.append("• ").append(r.path("leaveType").asText("Leave")).append(", ")
                    .append(period(r.path("startDate").asText(), r.path("endDate").asText()))
                    .append(" (").append(days(r.path("days"))).append(") - ").append(status(r.path("status").asText())).append('\n');
        }
        return text.toString().strip();
    }

    private String pendingLeave(JsonNode data) {
        JsonNode requests = data.path("requests");
        if (!requests.isArray()) return null;
        int count = data.path("count").asInt(requests.size());
        if (count == 0) return "Nothing is waiting for your approval right now.";
        StringBuilder text = new StringBuilder(count + (count == 1 ? " leave request is" : " leave requests are") + " waiting for your approval:\n");
        int shown = 0;
        for (JsonNode r : requests) {
            if (shown++ == 5) break;
            text.append("• ").append(r.path("employeeName").asText("Employee")).append(" - ")
                    .append(r.path("leaveType").asText("Leave")).append(", ")
                    .append(period(r.path("startDate").asText(), r.path("endDate").asText()))
                    .append(" (").append(days(r.path("days"))).append(")\n");
        }
        if (count > 5) text.append("…and ").append(count - 5).append(" more.\n");
        return text.append("\nYou can approve or reject them on the Leave page.").toString().strip();
    }

    private String payslips(JsonNode data) {
        JsonNode rows = data.path("recentPayroll");
        if (!rows.isArray()) return null;
        if (rows.isEmpty()) return "No processed payslips are available for you yet.";
        JsonNode latest = rows.get(0);
        StringBuilder text = new StringBuilder("Your latest payslip");
        String paid = latest.path("paymentDate").asText("");
        if (!paid.isBlank() && !"null".equals(paid)) text.append(" (paid ").append(date(paid)).append(")");
        text.append(":\n• Gross: ").append(rupees(latest.path("grossSalary")))
                .append("\n• Deductions: ").append(rupees(latest.path("totalDeductions")))
                .append("\n• Net pay: **").append(rupees(latest.path("netSalary"))).append("**");
        if (rows.size() > 1) text.append("\n\nYou can download all payslips as PDF from My Pay.");
        return text.toString();
    }

    private String salary(JsonNode data) {
        if (data.has("salaryStructure")) return "There's no active salary structure for you yet. Please check with HR.";
        if (!data.has("grossSalary")) return null;
        return "Your current monthly salary:\n• Gross: " + rupees(data.path("grossSalary"))
                + "\n• Deductions: " + rupees(data.path("totalDeductions"))
                + "\n• Net: **" + rupees(data.path("netSalary")) + "**"
                + "\n\n(effective from " + date(data.path("effectiveFrom").asText()) + ")";
    }

    private String myAttendance(JsonNode data) {
        JsonNode records = data.path("records");
        if (!records.isArray()) return null;
        if (records.isEmpty()) {
            return "I can't see any biometric punches for that day. If you checked in from the browser, "
                    + "your check-in is shown on My Attendance.";
        }
        List<String> times = new ArrayList<>();
        for (JsonNode r : records) times.add(0, time(r.path("dateTime").asText()) + " " + punch(r.path("punchType").asText()));
        return "Your punches: " + String.join(", ", times) + ".";
    }

    private String teamAttendance(JsonNode data) {
        JsonNode records = data.path("records");
        if (!records.isArray()) return null;
        java.util.Set<String> people = new java.util.LinkedHashSet<>();
        for (JsonNode r : records) people.add(r.path("employeeName").asText("Employee"));
        if (people.isEmpty()) return "No punches are recorded for your team on that day yet.";
        List<String> names = new ArrayList<>(people);
        String list = String.join(", ", names.subList(0, Math.min(10, names.size())));
        return people.size() + (people.size() == 1 ? " person has" : " people have") + " punched in: " + list
                + (people.size() > 10 ? " and " + (people.size() - 10) + " more." : ".");
    }

    private String myDocuments(JsonNode data) {
        int count = data.path("documentCount").asInt(-1);
        if (count < 0) return null;
        if (count == 0) return "You haven't uploaded any documents yet. You can add them from My Documents.";
        List<String> parts = new ArrayList<>();
        data.path("statusCounts").fields().forEachRemaining(e -> parts.add(e.getValue().asInt() + " " + status(e.getKey()).toLowerCase(Locale.ROOT)));
        return "You have " + count + (count == 1 ? " document" : " documents") + (parts.isEmpty() ? "." : ": " + String.join(", ", parts) + ".");
    }

    private String pendingDocuments(JsonNode data) {
        int count = data.path("count").asInt(-1);
        if (count < 0) return null;
        if (count == 0) return "No employee documents are waiting for review.";
        StringBuilder text = new StringBuilder(count + (count == 1 ? " document is" : " documents are") + " waiting for review:\n");
        int shown = 0;
        for (JsonNode d : data.path("documents")) {
            if (shown++ == 5) break;
            text.append("• ").append(d.path("employeeName").asText("Employee")).append(" - ").append(d.path("documentType").asText("Document")).append('\n');
        }
        return text.toString().strip();
    }

    private String offlineDevices(JsonNode data) {
        int count = data.path("count").asInt(-1);
        if (count < 0) return null;
        if (count == 0) return "All managed devices are online.";
        StringBuilder text = new StringBuilder(count + (count == 1 ? " device is" : " devices are") + " offline:\n");
        int shown = 0;
        for (JsonNode d : data.path("devices")) {
            if (shown++ == 5) break;
            text.append("• ").append(d.path("deviceName").asText("Device"));
            String who = d.path("employeeName").asText("");
            if (!who.isBlank()) text.append(" (").append(who).append(")");
            text.append('\n');
        }
        return text.toString().strip();
    }

    // ------------------------------------------------------------------ helpers

    private static String days(JsonNode value) {
        double v = value.asDouble(0);
        String n = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return n + (v == 1 ? " day" : " days");
    }

    private static String rupees(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "-";
        // java.text formats en-IN as 125,000; Indian grouping (1,25,000) is done by hand.
        long amount = new BigDecimal(value.asText("0")).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        String digits = Long.toString(Math.abs(amount));
        StringBuilder grouped = new StringBuilder();
        if (digits.length() > 3) {
            String head = digits.substring(0, digits.length() - 3);
            for (int i = head.length(); i > 0; i -= 2) {
                grouped.insert(0, head.substring(Math.max(0, i - 2), i)).insert(0, ',');
            }
            grouped.deleteCharAt(0).append(',').append(digits.substring(digits.length() - 3));
        } else {
            grouped.append(digits);
        }
        return (amount < 0 ? "-₹" : "₹") + grouped;
    }

    private static String date(String iso) {
        try {
            return LocalDate.parse(iso.length() > 10 ? iso.substring(0, 10) : iso).format(DAY);
        } catch (Exception ex) {
            return iso;
        }
    }

    private static String period(String start, String end) {
        return start.equals(end) ? date(start) : date(start) + " to " + date(end);
    }

    private static String time(String iso) {
        try {
            return LocalDateTime.parse(iso.length() > 19 ? iso.substring(0, 19) : iso).format(TIME);
        } catch (Exception ex) {
            return iso;
        }
    }

    private static String punch(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "IN", "CHECK_IN" -> "(in)";
            case "OUT", "CHECK_OUT" -> "(out)";
            default -> "";
        };
    }

    private static String status(String raw) {
        return switch (raw == null ? "" : raw.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Pending approval";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            case "CANCELLED" -> "Cancelled";
            case "VERIFIED" -> "Verified";
            default -> raw == null || raw.isBlank() ? "Unknown" : raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
