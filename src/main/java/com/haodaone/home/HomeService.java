package com.haodaone.home;

import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.esign.EsignService;
import com.haodaone.exit.ResignationService;
import com.haodaone.expense.service.ExpenseClaimService;
import com.haodaone.helpdesk.HelpdeskService;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.loan.LoanService;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.performance.suite.PerfContext;
import com.haodaone.performance.suite.PipService;
import com.haodaone.performance.suite.ReviewCycleService;
import com.haodaone.probation.ProbationService;
import com.haodaone.profile.ProfileService;
import com.haodaone.salary.fbp.FbpService;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.survey.SurveyService;
import com.haodaone.timesheet.TimesheetService;
import com.haodaone.travel.TravelService;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * The home page's "what needs me today" data, gathered from every module for the signed-in person:
 * their to-dos (approvals, documents to sign, reviews, surveys, tickets...), today's team pulse (who is out,
 * birthdays, work anniversaries), an IT snapshot for admins, and workspace setup progress for new companies.
 * Not transactional on purpose: each module is asked separately through its own service, so one module that
 * fails or refuses (no permission) only drops its own line instead of breaking the page.
 */
@Service
public class HomeService {

    private static final Logger log = LoggerFactory.getLogger(HomeService.class);

    private final LeaveRequestService leave;
    private final TimesheetService timesheets;
    private final ExpenseClaimService expenses;
    private final EsignService esign;
    private final ReviewCycleService reviews;
    private final SurveyService surveys;
    private final ProbationService probation;
    private final HelpdeskService helpdesk;
    private final PipService pips;
    private final TravelService travel;
    private final ProfileService profile;
    private final LoanService loans;
    private final ResignationService resignations;
    private final FbpService fbp;
    private final EmployeeDocumentService documents;
    private final AuthorizationService authorization;
    private final PerfContext ctx;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final MonitoredDeviceRepository monitoredDevices;
    private final CompanyRepository companies;
    private final DepartmentRepository departments;
    private final LeaveTypeRepository leaveTypes;
    private final HolidayRepository holidays;
    private final DeviceRepository biometricDevices;
    private final OfficeLocationRepository officeLocations;
    private final SalaryStructureRepository salaryStructures;
    private final PayrollRunRepository payrollRuns;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final Clock clock;

    public HomeService(LeaveRequestService leave, TimesheetService timesheets, ExpenseClaimService expenses, EsignService esign,
                       ReviewCycleService reviews, SurveyService surveys, ProbationService probation, HelpdeskService helpdesk,
                       PipService pips, TravelService travel, ProfileService profile, LoanService loans, ResignationService resignations,
                       FbpService fbp, EmployeeDocumentService documents, AuthorizationService authorization, PerfContext ctx, EmployeeRepository employees,
                       LeaveRequestRepository leaveRequests, MonitoredDeviceRepository monitoredDevices, CompanyRepository companies,
                       DepartmentRepository departments, LeaveTypeRepository leaveTypes, HolidayRepository holidays,
                       DeviceRepository biometricDevices, OfficeLocationRepository officeLocations,
                       SalaryStructureRepository salaryStructures, PayrollRunRepository payrollRuns, UserRepository users,
                       TransactionTemplate tx, Clock applicationClock) {
        this.leave = leave;
        this.timesheets = timesheets;
        this.expenses = expenses;
        this.esign = esign;
        this.reviews = reviews;
        this.surveys = surveys;
        this.probation = probation;
        this.helpdesk = helpdesk;
        this.pips = pips;
        this.travel = travel;
        this.profile = profile;
        this.loans = loans;
        this.resignations = resignations;
        this.fbp = fbp;
        this.documents = documents;
        this.authorization = authorization;
        this.ctx = ctx;
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.monitoredDevices = monitoredDevices;
        this.companies = companies;
        this.departments = departments;
        this.leaveTypes = leaveTypes;
        this.holidays = holidays;
        this.biometricDevices = biometricDevices;
        this.officeLocations = officeLocations;
        this.salaryStructures = salaryStructures;
        this.payrollRuns = payrollRuns;
        this.users = users;
        this.tx = tx;
        this.clock = applicationClock;
    }

    /** One line in "Your to-dos". tone: action (needs a decision), self (something only you can do). */
    public record Todo(String key, String title, String detail, int count, String link, String tone) { }
    public record Person(Long employeeId, String name, String photoUrl, String detail, LocalDate date) { }
    public record ItSnapshot(int devices, int online, int notSeenForWeek, int unassigned) { }
    public record Today(List<Person> outToday, List<Person> birthdays, List<Person> anniversaries, ItSnapshot it) { }
    public record SetupStep(String key, String title, String detail, boolean done, String link) { }
    public record Setup(boolean visible, int done, int total, List<SetupStep> steps) { }

    // ------------------------------------------------------------------ to-dos

    public List<Todo> todos() {
        Long myId = Optional.ofNullable(ctx.meOrNull()).map(Employee::getId).orElse(null);
        List<Todo> out = new ArrayList<>();

        if (has("LEAVE_APPROVE")) {
            boolean orgWide = authorization.hasOrganizationScope("LEAVE_APPROVE");
            add(out, "leave", "Leave requests to decide", "/approvals", "action", () -> (int) leave.listForManagerTeam(username(), "PENDING").stream()
                    .filter(r -> orgWide || r.getPendingWith() == null || Objects.equals(r.getPendingApproverEmployeeId(), myId)).count());
        }
        if (has("TIMESHEET_APPROVE") || has("TIMESHEET_MANAGE"))
            add(out, "timesheets", "Timesheets to approve", "/approvals", "action", () -> timesheets.pendingApprovals().size());
        if (has("EXPENSE_APPROVE") || has("EXPENSE_MANAGE"))
            add(out, "expenses", "Expense claims to review", "/expenses/approvals", "action", () -> expenses.managerQueue().size() + (authorization.hasOrganizationScope("EXPENSE_MANAGE") ? expenses.financeQueue().size() : 0));
        add(out, "travel", "Trips to approve", "/travel", "action", () -> (int) travel.queue("PENDING").stream().filter(TravelService.TripDTO::canDecide).count());
        if (has("EMPLOYEE_MANAGE")) {
            add(out, "profile", "Profile changes to approve", "/approvals", "action", () -> profile.pending().size());
            add(out, "documents", "Documents expiring soon", "/employees", "action", () -> documents.expiringSoon(30).size());
        }
        if (has("EMPLOYEE_MANAGE") || has("LEAVE_APPROVE"))
            add(out, "resignations", "Resignations to review", "/exits", "action", () -> resignations.pendingForMe().size());
        if (has("SALARY_MANAGE")) {
            add(out, "loans", "Loan requests", "/salary/loans", "action", () -> loans.list("REQUESTED").size());
            add(out, "fbp", "Benefit claims to approve", "/salary/fbp", "action", () -> fbp.claims("SUBMITTED").size());
        }
        add(out, "probation", "Probation reviews due", "/probation", "action", () -> (int) probation.list().stream()
                .filter(p -> (p.canRecommend() && "MANAGER_REVIEW".equals(p.status())) || (p.canDecide() && "HR_REVIEW".equals(p.status()))).count());

        if (myId != null) {
            add(out, "sign", "Documents to sign", "/my-profile?tab=signatures", "self", () -> (int) esign.mine().stream().filter(EsignService.RequestDTO::canSign).count());
            add(out, "reviews", "Reviews to write", "/performance/reviews", "self", () -> reviews.myTasks().size());
            add(out, "surveys", "Surveys waiting for your answer", "/company?tab=surveys", "self", () -> (int) surveys.forMe().stream()
                    .filter(s -> "OPEN".equals(s.status()) && !s.answered()).count());
            add(out, "tickets", "Helpdesk replies waiting for you", "/helpdesk", "self", () -> (int) helpdesk.mine().stream()
                    .filter(t -> "WAITING_ON_EMPLOYEE".equals(t.status()) || "RESOLVED".equals(t.status())).count());
            add(out, "pip", "Improvement plan to acknowledge", "/performance/improvement-plans", "self", () -> (int) pips.mine().stream()
                    .filter(p -> p.acknowledgedAt() == null && "ACTIVE".equals(p.status())).count());
        }
        return out;
    }

    private void add(List<Todo> out, String key, String title, String link, String tone, Supplier<Integer> count) {
        try {
            Integer n = count.get();
            if (n != null && n > 0) out.add(new Todo(key, title, detail(key, n), n, link, tone));
        } catch (RuntimeException ex) {
            // No access to this module, or it is not set up for the company: the line is simply left out.
            log.debug("Home to-do '{}' skipped: {}", key, ex.getMessage());
        }
    }

    private static String detail(String key, int n) {
        return switch (key) {
            case "sign" -> n == 1 ? "1 document is waiting for your signature" : n + " documents are waiting for your signature";
            case "reviews" -> n == 1 ? "1 review is open" : n + " reviews are open";
            case "surveys" -> n == 1 ? "1 survey takes about a minute" : n + " surveys are open";
            case "tickets" -> n == 1 ? "HR replied to 1 ticket" : "HR replied to " + n + " tickets";
            case "pip" -> "Please read and acknowledge it";
            case "documents" -> n == 1 ? "1 employee document expires within 30 days" : n + " employee documents expire within 30 days";
            default -> n == 1 ? "1 waiting for you" : n + " waiting for you";
        };
    }

    // ------------------------------------------------------------------ today

    public Today today() {
        return tx.execute(status -> {
            Long companyId = ctx.companyId();
            LocalDate today = LocalDate.now(clock);
            List<Person> out = leaveRequests.findActiveOnForCompany(companyId, today).stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .map(r -> new Person(r.getEmployee().getId(), r.getEmployee().getFullName(), r.getEmployee().getProfilePhotoUrl(),
                            r.isHalfDay() ? "Half day" : r.getEndDate().equals(today) ? "Back tomorrow" : "Back after " + r.getEndDate(), r.getEndDate()))
                    .sorted(Comparator.comparing(Person::name)).limit(30).toList();

            List<Employee> active = employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream().filter(this::employed).toList();
            List<Person> birthdays = new ArrayList<>();
            List<Person> anniversaries = new ArrayList<>();
            for (Employee e : active) {
                if (e.getDateOfBirth() != null) {
                    LocalDate next = nextOccurrence(e.getDateOfBirth(), today);
                    if (ChronoUnit.DAYS.between(today, next) <= 14)
                        birthdays.add(new Person(e.getId(), e.getFullName(), e.getProfilePhotoUrl(), relative(today, next), next));
                }
                if (e.getDateOfJoining() != null && e.getDateOfJoining().isBefore(today)) {
                    LocalDate next = nextOccurrence(e.getDateOfJoining(), today);
                    long years = ChronoUnit.YEARS.between(e.getDateOfJoining(), next);
                    if (years >= 1 && ChronoUnit.DAYS.between(today, next) <= 14)
                        anniversaries.add(new Person(e.getId(), e.getFullName(), e.getProfilePhotoUrl(),
                                years + (years == 1 ? " year" : " years") + " · " + relative(today, next), next));
                }
            }
            birthdays.sort(Comparator.comparing(Person::date));
            anniversaries.sort(Comparator.comparing(Person::date));

            ItSnapshot it = null;
            if (has("MONITORING_VIEW") || has("IT_DEVICE_VIEW")) {
                List<MonitoredDevice> devices = monitoredDevices.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(companyId).stream()
                        .filter(MonitoredDevice::isActive).toList();
                LocalDateTime weekAgo = LocalDateTime.now(clock).minusDays(7);
                it = new ItSnapshot(devices.size(),
                        (int) devices.stream().filter(MonitoredDevice::isOnline).count(),
                        (int) devices.stream().filter(d -> d.getLastSeenAt() == null || d.getLastSeenAt().isBefore(weekAgo)).count(),
                        (int) devices.stream().filter(d -> d.getEmployee() == null).count());
            }
            return new Today(out, birthdays.stream().limit(8).toList(), anniversaries.stream().limit(8).toList(), it);
        });
    }

    // ------------------------------------------------------------------ setup

    /** Setup progress for workspace admins; hidden for everyone else. */
    public Setup setup() {
        boolean admin = authorization.hasOrganizationScope("EMPLOYEE_MANAGE");
        if (!admin) return new Setup(false, 0, 0, List.of());
        return tx.execute(status -> {
            Long companyId = ctx.companyId();
            LocalDate today = LocalDate.now(clock);
            var company = companies.findById(companyId).orElse(null);
            int people = (int) employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream().filter(this::employed).count();
            List<SetupStep> steps = List.of(
                    new SetupStep("company", "Company details", "Add your industry and GSTIN so letters and invoices are correct",
                            company != null && (notBlank(company.getIndustry()) || notBlank(company.getGstin())), "/settings/organization"),
                    new SetupStep("departments", "Departments", "Create the teams people belong to",
                            !departments.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).isEmpty(), "/organization/structure"),
                    new SetupStep("employees", "Add your employees", people > 1 ? people + " people added" : "Import everyone at once from Excel",
                            people > 1, "/employees/import"),
                    new SetupStep("leave", "Leave policy", "Set leave types and how many days people get",
                            !leaveTypes.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).isEmpty(), "/settings/leave"),
                    new SetupStep("holidays", "Holiday calendar", "Add this year's holidays",
                            !holidays.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31)).isEmpty(), "/settings/leave"),
                    new SetupStep("attendance", "Attendance", "Connect a biometric machine or add an office location for phone check-in",
                            !biometricDevices.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(companyId).isEmpty()
                                    || !officeLocations.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).isEmpty(), "/settings/organization"),
                    new SetupStep("salary", "Salary structures", "Set up pay for your employees",
                            salaryStructures.countActive(companyId) > 0, "/salary/employees"),
                    new SetupStep("team", "Invite your team", "Give managers and HR their own logins",
                            users.findAllByCompanyIdAndDeletedFalse(companyId).size() > 1, "/settings/users"),
                    new SetupStep("payroll", "Run your first payroll", "Generate payslips for the month",
                            !payrollRuns.findAllByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(companyId).isEmpty(), "/salary/payroll-processing"));
            int done = (int) steps.stream().filter(SetupStep::done).count();
            return new Setup(done < steps.size(), done, steps.size(), steps);
        });
    }

    // ------------------------------------------------------------------ helpers

    private boolean employed(Employee e) {
        String s = EmploymentStatus.normalize(e.getStatus());
        return !EmploymentStatus.RESIGNED.equals(s) && !EmploymentStatus.TERMINATED.equals(s);
    }

    static LocalDate nextOccurrence(LocalDate date, LocalDate today) {
        MonthDay md = MonthDay.from(date);
        LocalDate thisYear = md.isValidYear(today.getYear()) ? md.atYear(today.getYear()) : LocalDate.of(today.getYear(), 2, 28);
        if (thisYear.isBefore(today)) {
            int y = today.getYear() + 1;
            return md.isValidYear(y) ? md.atYear(y) : LocalDate.of(y, 2, 28);
        }
        return thisYear;
    }

    private static String relative(LocalDate today, LocalDate when) {
        long days = ChronoUnit.DAYS.between(today, when);
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        return when.getDayOfMonth() + " " + when.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean has(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) if (authority.equals(a.getAuthority())) return true;
        return false;
    }

    private static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
