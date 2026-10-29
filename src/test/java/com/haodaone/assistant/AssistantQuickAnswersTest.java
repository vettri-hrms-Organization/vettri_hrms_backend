package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantQuickAnswersTest {

    private final AssistantQuickAnswers quick = new AssistantQuickAnswers(new ObjectMapper());
    private static final Set<String> EMPLOYEE_TOOLS = Set.of("get_my_leave_balance", "get_my_leave_requests",
            "get_my_payslip", "get_my_salary", "get_my_attendance", "get_my_documents", "prepare_leave_request");

    @ParameterizedTest
    @ValueSource(strings = {"What's my leave balance?", "how many leaves do I have left", "remaining leaves", "Leave balance"})
    void routesBalanceQuestions(String message) {
        assertThat(quick.route(message, EMPLOYEE_TOOLS)).get().extracting(AssistantQuickAnswers.Route::tool)
                .isEqualTo("get_my_leave_balance");
    }

    @Test
    void routesPayslipSalaryAndAttendance() {
        assertThat(quick.route("show my last payslip", EMPLOYEE_TOOLS).get().tool()).isEqualTo("get_my_payslip");
        assertThat(quick.route("what is my salary", EMPLOYEE_TOOLS).get().tool()).isEqualTo("get_my_salary");
        assertThat(quick.route("did I check in today?", EMPLOYEE_TOOLS).get().tool()).isEqualTo("get_my_attendance");
        assertThat(quick.route("did I check in today?", EMPLOYEE_TOOLS).get().arguments()).containsKey("date");
    }

    @ParameterizedTest
    @ValueSource(strings = {"How do I apply for leave?", "Where can I see my payslip?", "Why was my leave rejected?",
            "Apply casual leave tomorrow", "Tell me a joke"})
    void leavesGuidanceActionsAndOpenQuestionsToTheModel(String message) {
        assertThat(quick.route(message, EMPLOYEE_TOOLS)).isEmpty();
    }

    @Test
    void neverRoutesToAToolTheUserDoesNotHave() {
        assertThat(quick.route("pending leave approvals", EMPLOYEE_TOOLS)).isEmpty();
        assertThat(quick.route("pending leave approvals", Set.of("get_pending_leave_requests")).get().tool())
                .isEqualTo("get_pending_leave_requests");
    }

    @Test
    void greetingAndHelpListOnlyAvailableCapabilities() {
        assertThat(quick.isGreetingOrHelp("Hello")).isTrue();
        assertThat(quick.isGreetingOrHelp("good morning!")).isTrue();
        assertThat(quick.isGreetingOrHelp("hello, what is my balance")).isFalse();
        String help = quick.help(null, Set.of("get_my_leave_balance"));
        assertThat(help).contains("leave balance").doesNotContain("approval", "offline");
    }

    @Test
    void formatsLeaveBalanceAndHighlightsTheAskedType() {
        String json = "{\"year\":2026,\"balances\":[{\"leaveType\":\"Casual Leave\",\"allocatedDays\":12,\"usedDays\":3,\"remainingDays\":9},"
                + "{\"leaveType\":\"Sick Leave\",\"allocatedDays\":10,\"usedDays\":0.5,\"remainingDays\":9.5}]}";
        assertThat(quick.format("get_my_leave_balance", json, "my balance").get())
                .contains("Casual Leave: **9 days**", "Sick Leave: **9.5 days**", "2026");
        assertThat(quick.format("get_my_leave_balance", json, "how much sick leave do I have").get())
                .isEqualTo("You have **9.5 days** of Sick Leave left this year (0.5 days used of 10 days).");
    }

    @Test
    void formatsPayslipInRupeesWithIndianGrouping() {
        String json = "{\"recentPayroll\":[{\"paymentDate\":\"2026-10-31\",\"status\":\"PAID\",\"grossSalary\":125000,"
                + "\"totalDeductions\":15400,\"netSalary\":109600,\"remarks\":\"\"}]}";
        assertThat(quick.format("get_my_payslip", json, "payslip").get())
                .contains("31 Oct 2026", "₹1,25,000", "₹15,400", "**₹1,09,600**");
    }

    @Test
    void formatsPendingApprovalsAndEmptyStates() {
        assertThat(quick.format("get_pending_leave_requests", "{\"count\":0,\"requests\":[]}", "").get())
                .isEqualTo("Nothing is waiting for your approval right now.");
        String json = "{\"count\":1,\"requests\":[{\"employeeName\":\"Priya Nair\",\"leaveType\":\"Casual Leave\","
                + "\"startDate\":\"2026-11-03\",\"endDate\":\"2026-11-04\",\"days\":2}]}";
        assertThat(quick.format("get_pending_leave_requests", json, "").get())
                .contains("1 leave request is waiting", "Priya Nair - Casual Leave, 3 Nov 2026 to 4 Nov 2026 (2 days)");
    }

    @Test
    void unknownToolsOrUnexpectedShapesFallBackToTheModel() {
        assertThat(quick.format("get_navigation_guidance", "{}", "")).isEmpty();
        assertThat(quick.format("get_my_leave_balance", "{\"remainingDays\":8}", "")).isEmpty();
        assertThat(quick.format("get_my_payslip", "not json", "")).isEmpty();
    }
}
