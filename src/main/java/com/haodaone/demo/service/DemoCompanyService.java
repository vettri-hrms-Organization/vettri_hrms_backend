package com.haodaone.demo.service;

import com.haodaone.announcement.entity.Announcement;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.entity.BaseEntity;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.customfield.entity.CustomFieldDefinition;
import com.haodaone.customfield.entity.CustomFieldType;
import com.haodaone.customfield.entity.EmployeeCustomFieldValue;
import com.haodaone.customfield.service.CustomFieldValidator;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.expense.entity.ExpenseCategory;
import com.haodaone.expense.entity.ExpenseClaim;
import com.haodaone.expense.entity.ExpenseClaimLine;
import com.haodaone.expense.service.ExpenseCategoryService;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.entity.LeaveBalance;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.service.DefaultLeaveTypes;
import com.haodaone.onboarding.entity.ChecklistConstants;
import com.haodaone.onboarding.entity.EmployeeChecklist;
import com.haodaone.onboarding.entity.EmployeeChecklistTask;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.entity.Team;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.entity.JobOpening;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
import com.haodaone.salary.dto.PayrollRunDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.service.PayrollService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Sales demo workspaces (SUPER_ADMIN only): a realistic ~40 person Indian
 * company with org structure, salaries (auto statutory), 3 months of
 * attendance, leave, holidays, 2 processed payroll runs, hiring pipeline,
 * announcements, expenses and an onboarding checklist in progress.
 *
 * Reset soft-deletes every company-scoped row and seeds again. Employee codes,
 * emails and usernames are globally unique (soft-deleted rows included), so
 * each seed uses a fresh generation tag inside them. Data comes from a fixed
 * random seed, so counts are identical after every reset. Companies are
 * flagged Company.isDemo(): NotificationService sends no email/WhatsApp for them.
 */
@Service
public class DemoCompanyService {

    private static final Logger log = LoggerFactory.getLogger(DemoCompanyService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final String EMAIL_DOMAIN = "demo.vettrihrms.invalid";
    static final long SEED = 20261028L;

    /** {department, code, headcount}. Sums to 40. */
    private static final Object[][] DEPARTMENTS = {
            {"Operations", "OPS", 6}, {"Engineering", "ENG", 12}, {"Sales", "SAL", 7}, {"Marketing", "MKT", 4},
            {"Human Resources", "HR", 3}, {"Finance", "FIN", 4}, {"Customer Support", "CS", 4}};
    private static final Map<String, String[]> DESIGNATIONS = Map.of(
            "OPS", new String[]{"Managing Director", "Operations Manager", "Operations Executive"},
            "ENG", new String[]{"Engineering Manager", "Senior Software Engineer", "Software Engineer"},
            "SAL", new String[]{"Sales Manager", "Senior Sales Executive", "Sales Executive"},
            "MKT", new String[]{"Marketing Manager", "Digital Marketing Specialist", "Content Writer"},
            "HR", new String[]{"HR Manager", "HR Generalist", "Recruiter"},
            "FIN", new String[]{"Finance Manager", "Senior Accountant", "Accounts Executive"},
            "CS", new String[]{"Support Manager", "Senior Support Associate", "Support Associate"});
    private static final String[] MALE = {"Aarav", "Vivaan", "Arjun", "Karthik", "Rahul", "Siddharth", "Vikram", "Rohan",
            "Aditya", "Harish", "Naveen", "Pranav", "Suresh", "Manoj", "Deepak", "Ganesh", "Ashwin", "Imran", "Joseph", "Sanjay"};
    private static final String[] FEMALE = {"Priya", "Ananya", "Divya", "Kavya", "Meera", "Sneha", "Lakshmi", "Pooja",
            "Nandini", "Swathi", "Aishwarya", "Revathi", "Fatima", "Shreya", "Ishita", "Keerthana", "Bhavana", "Janani", "Neha", "Ritu"};
    private static final String[] LAST = {"Sharma", "Iyer", "Reddy", "Nair", "Krishnan", "Patel", "Menon", "Rao", "Gupta",
            "Subramanian", "Pillai", "Venkatesh", "Das", "Mukherjee", "Joshi", "Kulkarni", "Chandrasekaran", "Bhat", "Singh", "Thomas"};
    private static final String[] CITIES = {"Chennai", "Bengaluru", "Coimbatore", "Hyderabad", "Pune"};

    public record DemoCredentials(Long companyId, String companyName, String adminUsername, String adminPassword,
                                  Map<String, Long> counts) {
    }

    public record DemoCompanySummary(Long id, String name, LocalDateTime createdAt, long employees) {
    }

    @PersistenceContext
    private EntityManager em;

    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roles;
    private final LeaveTypeRepository leaveTypes;
    private final PayrollService payrollService;
    private final ExpenseCategoryService expenseCategoryService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DemoCompanyService(PasswordEncoder passwordEncoder, RoleRepository roles, LeaveTypeRepository leaveTypes,
                              PayrollService payrollService, ExpenseCategoryService expenseCategoryService,
                              AuditLogService auditLogService) {
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
        this.leaveTypes = leaveTypes;
        this.payrollService = payrollService;
        this.expenseCategoryService = expenseCategoryService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<DemoCompanySummary> list() {
        List<Company> companies = em.createQuery(
                "SELECT c FROM Company c WHERE c.demo = true AND c.deleted = false ORDER BY c.id DESC", Company.class)
                .getResultList();
        List<DemoCompanySummary> result = new ArrayList<>();
        for (Company company : companies) {
            result.add(new DemoCompanySummary(company.getId(), company.getName(), company.getCreatedAt(),
                    countEmployees(company.getId())));
        }
        return result;
    }

    @Transactional
    public DemoCredentials create(String requestedName) {
        String tag = generationTag();
        String name = requestedName == null || requestedName.isBlank()
                ? "Sundaram Tech Solutions (Demo " + tag.toUpperCase(Locale.ROOT) + ")"
                : requestedName.trim();
        if (name.length() > 255) throw new BadRequestException("Name is too long");
        Long clash = em.createQuery("SELECT COUNT(c) FROM Company c WHERE LOWER(c.name) = :name AND c.deleted = false", Long.class)
                .setParameter("name", name.toLowerCase(Locale.ROOT)).getSingleResult();
        if (clash > 0) throw new BadRequestException("A company with that name already exists");

        Company company = new Company();
        company.setName(name);
        company.setIndustry("Information Technology");
        company.setCompanySize("11-50");
        company.setCountry("India");
        company.setLegalName("Sundaram Tech Solutions Private Limited");
        company.setBillingAddress("4th Floor, Olympia Tech Park, Guindy, Chennai, Tamil Nadu 600032");
        company.setDemo(true);
        em.persist(company);
        em.flush();
        DemoCredentials credentials = seed(company, tag);
        auditLogService.log("Company", company.getId(), "DEMO_CREATE", "Created demo workspace '" + name + "'");
        return credentials;
    }

    @Transactional
    public DemoCredentials reset(Long companyId) {
        Company company = em.find(Company.class, companyId);
        if (company == null || company.isDeleted()) throw new ResourceNotFoundException("Company not found");
        if (!company.isDemo()) throw new BadRequestException("Only demo companies can be reset");
        wipe(companyId);
        company = em.find(Company.class, companyId);
        DemoCredentials credentials = seed(company, generationTag());
        auditLogService.log("Company", companyId, "DEMO_RESET", "Reset demo workspace '" + company.getName() + "'");
        return credentials;
    }

    // ------------------------------------------------------------------ wipe

    /** Soft-deletes every company-scoped BaseEntity row (except 1:1 settings and the subscription) and disables old logins. */
    void wipe(Long companyId) {
        LocalDateTime now = LocalDateTime.now(IST);
        em.flush();
        em.createQuery("UPDATE User u SET u.active = false, u.accountStatus = 'DISABLED' WHERE u.company.id = :cid")
                .setParameter("cid", companyId).executeUpdate();
        for (EntityType<?> type : em.getMetamodel().getEntities()) {
            Class<?> javaType = type.getJavaType();
            if (!BaseEntity.class.isAssignableFrom(javaType) || javaType == Company.class || javaType == Subscription.class) continue;
            if (!hasCompanyAttribute(type) || isOneToOneCompany(javaType)) continue;
            em.createQuery("UPDATE " + type.getName() + " e SET e.deleted = true, e.deletedAt = :now "
                            + "WHERE e.company.id = :cid AND e.deleted = false")
                    .setParameter("now", now).setParameter("cid", companyId).executeUpdate();
        }
        // notification is a JDBC-only table (Flyway); it does not exist in the Hibernate-generated test schema.
        Number notificationTable = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'notification'").getSingleResult();
        if (notificationTable != null && notificationTable.longValue() > 0) {
            em.createNativeQuery("UPDATE notification SET deleted = TRUE WHERE company_id = ?1")
                    .setParameter(1, companyId).executeUpdate();
        }
        em.clear();
    }

    private static boolean hasCompanyAttribute(EntityType<?> type) {
        for (Attribute<?, ?> attribute : type.getAttributes()) {
            if ("company".equals(attribute.getName()) && Company.class.equals(attribute.getJavaType())) return true;
        }
        return false;
    }

    private static boolean isOneToOneCompany(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("company");
                JoinColumn join = field.getAnnotation(JoinColumn.class);
                return field.isAnnotationPresent(OneToOne.class) || (join != null && join.unique());
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ seed

    private DemoCredentials seed(Company company, String tag) {
        Random random = new Random(SEED);
        Long companyId = company.getId();
        String code = "D" + companyId + tag;                  // e.g. D12k9x3a
        LocalDate today = LocalDate.now(IST);
        ensureSubscription(company, today);

        Role adminRole = roles.findByName("COMPANY_ADMIN").orElseThrow(() -> new BadRequestException("Roles are not seeded"));
        Role managerRole = roles.findByName("MANAGER").orElse(null);
        Role employeeRole = roles.findByName("EMPLOYEE").orElse(null);

        // --- org structure
        Map<String, Department> departments = new LinkedHashMap<>();
        Map<String, Designation[]> designations = new HashMap<>();
        Map<String, Team> teams = new HashMap<>();
        for (Object[] row : DEPARTMENTS) {
            String deptCode = (String) row[1];
            Department department = new Department();
            department.setCompany(company);
            department.setName((String) row[0]);
            department.setCode(deptCode);
            department.setDescription(row[0] + " department");
            em.persist(department);
            departments.put(deptCode, department);
            String[] titles = DESIGNATIONS.get(deptCode);
            Designation[] levels = new Designation[titles.length];
            for (int i = 0; i < titles.length; i++) {
                Designation designation = new Designation();
                designation.setCompany(company);
                designation.setTitle(titles[i]);
                designation.setLevel(titles.length - i);
                designation.setDepartment(department);
                em.persist(designation);
                levels[i] = designation;
            }
            designations.put(deptCode, levels);
            Team team = new Team();
            team.setCompany(company);
            team.setDepartment(department);
            team.setName(row[0] + " Core");
            em.persist(team);
            teams.put(deptCode, team);
        }

        // --- employees (+ a login for each; only the admin's password is handed out)
        List<Employee> employees = new ArrayList<>();
        Map<String, Employee> heads = new HashMap<>();
        Employee md = null;
        Employee recentJoiner = null;
        User adminUser = null;
        String adminPassword = randomPassword();
        int seq = 0;
        Set<String> usedNames = new HashSet<>();
        for (Object[] row : DEPARTMENTS) {
            String deptCode = (String) row[1];
            int headcount = (Integer) row[2];
            for (int i = 0; i < headcount; i++) {
                seq++;
                boolean female = random.nextBoolean();
                String first;
                String last;
                do {
                    first = female ? FEMALE[random.nextInt(FEMALE.length)] : MALE[random.nextInt(MALE.length)];
                    last = LAST[random.nextInt(LAST.length)];
                } while (!usedNames.add(first + " " + last));
                if ("HR".equals(deptCode) && i == 0) {
                    first = "Priya";
                    last = "Raman";
                    female = true;
                }
                Employee employee = new Employee();
                employee.setCompany(company);
                employee.setEmployeeCode(code + "-" + String.format("%02d", seq));
                employee.setFirstName(first);
                employee.setLastName(last);
                employee.setEmail(emailFor(first, last, seq, code));
                employee.setPhone("+9198" + String.format("%08d", 40000000 + random.nextInt(9_999_999)));
                employee.setGender(female ? "Female" : "Male");
                employee.setDateOfBirth(LocalDate.of(1980 + random.nextInt(20), 1 + random.nextInt(12), 1 + random.nextInt(28)));
                LocalDate joining = i == 0
                        ? LocalDate.of(2018 + random.nextInt(3), 1 + random.nextInt(12), 1 + random.nextInt(28))
                        : LocalDate.of(2020 + random.nextInt(5), 1 + random.nextInt(12), 1 + random.nextInt(28));
                employee.setDateOfJoining(joining);
                employee.setEmploymentType(i == headcount - 1 && "ENG".equals(deptCode) ? "INTERN" : "FULL_TIME");
                employee.setStatus(EmploymentStatus.ACTIVE);
                employee.setDepartment(departments.get(deptCode));
                Designation[] levels = designations.get(deptCode);
                employee.setDesignation(i == 0 ? levels[0] : (i <= headcount / 3 ? levels[1] : levels[2]));
                employee.setTeam(teams.get(deptCode));
                employee.setAddress(CITIES[random.nextInt(CITIES.length)] + ", India");
                employee.setBankName("HDFC Bank");
                employee.setBankIfsc("HDFC0001234");
                employee.setBankAccountNumber("5010" + String.format("%08d", random.nextInt(99_999_999)));
                employee.setPanNumber(pan(random, last));
                if (i == 0 && !"OPS".equals(deptCode)) {
                    employee.setReportingManager(md);
                } else if (i > 0) {
                    employee.setReportingManager(heads.get(deptCode));
                }

                User user = new User();
                user.setCompany(company);
                user.setFullName(first + " " + last);
                user.setActive(true);
                user.setAccountStatus("ACTIVE");
                user.setEmailVerified(true);
                Set<Role> userRoles = new HashSet<>();
                if ("HR".equals(deptCode) && i == 0) {
                    String username = "demo-admin-" + companyId + "-" + tag;
                    user.setUsername(username);
                    user.setEmail(username + "@" + EMAIL_DOMAIN);
                    user.setPasswordHash(passwordEncoder.encode(adminPassword));
                    userRoles.add(adminRole);
                    adminUser = user;
                } else {
                    user.setUsername(employee.getEmail());
                    user.setEmail(employee.getEmail());
                    user.setPasswordHash(passwordEncoder.encode(randomPassword()));
                    if (i == 0 && managerRole != null) userRoles.add(managerRole);
                }
                if (employeeRole != null) userRoles.add(employeeRole);
                user.setRoles(userRoles);
                em.persist(user);
                employee.setUser(user);
                em.persist(employee);
                employees.add(employee);
                if (i == 0) {
                    heads.put(deptCode, employee);
                    if ("OPS".equals(deptCode)) md = employee;
                }
            }
        }
        // Two recent hires (one still onboarding) so the onboarding and proration screens have data.
        recentJoiner = employees.get(employees.size() - 1);
        recentJoiner.setDateOfJoining(today.minusDays(6));
        employees.get(employees.size() - 5).setDateOfJoining(today.withDayOfMonth(1).minusMonths(1).plusDays(9));
        for (Map.Entry<String, Employee> head : heads.entrySet()) {
            departments.get(head.getKey()).setHeadEmployeeId(head.getValue().getId());
            teams.get(head.getKey()).setLeadEmployeeId(head.getValue().getId());
        }

        // --- salary structures (auto statutory; the payroll engine recomputes PF/ESI/PT/TDS)
        LocalDate periodStart = today.withDayOfMonth(1).minusMonths(3);
        for (Employee employee : employees) {
            String title = employee.getDesignation().getTitle();
            int monthlyGross;
            if (title.contains("Director")) monthlyGross = 350000;
            else if (title.contains("Manager")) monthlyGross = 140000 + random.nextInt(60) * 1000;
            else if (title.startsWith("Senior")) monthlyGross = 70000 + random.nextInt(40) * 1000;
            else if ("INTERN".equals(employee.getEmploymentType())) monthlyGross = 18000;
            else monthlyGross = 22000 + random.nextInt(35) * 1000;
            BigDecimal gross = BigDecimal.valueOf(monthlyGross);
            BigDecimal basic = gross.multiply(new BigDecimal("0.50")).setScale(0, RoundingMode.HALF_UP);
            BigDecimal hra = basic.multiply(new BigDecimal("0.40")).setScale(0, RoundingMode.HALF_UP);
            BigDecimal medical = new BigDecimal("1250");
            BigDecimal travel = new BigDecimal("1600");
            BigDecimal special = gross.subtract(basic).subtract(hra).subtract(medical).subtract(travel).max(BigDecimal.ZERO);
            SalaryComponents components = new SalaryComponents();
            components.setBasicSalary(basic);
            components.setHra(hra);
            components.setMedicalAllowance(medical);
            components.setTravelAllowance(travel);
            components.setSpecialAllowance(special);
            components.setPf(basic.multiply(new BigDecimal("0.12")).min(new BigDecimal("1800")).setScale(0, RoundingMode.HALF_UP));
            components.setProfessionalTax(new BigDecimal("200"));
            SalaryStructure structure = new SalaryStructure();
            structure.setCompany(company);
            structure.setEmployee(employee);
            structure.setEffectiveFrom(employee.getDateOfJoining().isAfter(periodStart) ? employee.getDateOfJoining() : periodStart);
            structure.setActive(true);
            structure.setComponents(components);
            structure.setAutoStatutory(true);
            structure.setNotes("Demo salary structure");
            structure.recalculate();
            em.persist(structure);
        }

        // --- leave types, balances, holidays
        DefaultLeaveTypes.seedFor(company, leaveTypes);
        LeaveType lop = new LeaveType();
        lop.setCompany(company);
        lop.setName("Loss of Pay");
        lop.setCode("LOP");
        lop.setDefaultDaysPerYear(0);
        lop.setPaid(false);
        em.persist(lop);
        List<LeaveType> paidTypes = leaveTypes.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(LeaveType::isPaid).toList();
        for (Employee employee : employees) {
            for (LeaveType type : paidTypes) {
                LeaveBalance balance = new LeaveBalance();
                balance.setCompany(company);
                balance.setEmployee(employee);
                balance.setLeaveType(type);
                balance.setYear(today.getYear());
                balance.setAllocatedDays(type.getDefaultDaysPerYear());
                balance.setCarriedForwardDays(type.isCarryForward() ? random.nextInt(6) : 0);
                em.persist(balance);
            }
        }
        Set<LocalDate> holidayDates = new HashSet<>();
        int year = today.getYear();
        Object[][] holidays = {
                {"Pongal", LocalDate.of(year, 1, 14)}, {"Republic Day", LocalDate.of(year, 1, 26)},
                {"Tamil New Year", LocalDate.of(year, 4, 14)}, {"May Day", LocalDate.of(year, 5, 1)},
                {"Independence Day", LocalDate.of(year, 8, 15)}, {"Ganesh Chaturthi", LocalDate.of(year, 9, 14)},
                {"Gandhi Jayanti", LocalDate.of(year, 10, 2)}, {"Ayudha Pooja", LocalDate.of(year, 10, 20)},
                {"Deepavali", LocalDate.of(year, 11, 8)}, {"Christmas", LocalDate.of(year, 12, 25)}};
        for (Object[] row : holidays) {
            Holiday holiday = new Holiday();
            holiday.setCompany(company);
            holiday.setName((String) row[0]);
            holiday.setDate((LocalDate) row[1]);
            em.persist(holiday);
            holidayDates.add((LocalDate) row[1]);
        }

        // --- leave requests (past approved/rejected, upcoming pending, one LOP in the last payroll month)
        Map<Long, Set<LocalDate>> onLeave = new HashMap<>();
        List<LocalDate> pastWorkdays = workdays(periodStart, today.minusDays(1), holidayDates);
        int leaveRequests = 0;
        for (int i = 0; i < employees.size(); i++) {
            Employee employee = employees.get(i);
            if (employee == recentJoiner || paidTypes.isEmpty()) continue;
            int approvedCount = i % 3;
            for (int k = 0; k < approvedCount && !pastWorkdays.isEmpty(); k++) {
                LocalDate start = pastWorkdays.get(random.nextInt(pastWorkdays.size()));
                if (start.isBefore(employee.getDateOfJoining())) continue;
                double days = random.nextInt(4) == 0 ? 2 : 1;
                LocalDate end = days == 2 ? nextWorkday(start, holidayDates) : start;
                leaveRequest(company, employee, paidTypes.get(random.nextInt(paidTypes.size())), start, end, days,
                        "APPROVED", employee.getReportingManager(), "Family function");
                onLeave.computeIfAbsent(employee.getId(), id -> new HashSet<>()).addAll(List.of(start, end));
                leaveRequests++;
            }
            if (i % 5 == 1) {
                LocalDate start = nextWorkday(today.plusDays(5 + random.nextInt(20)), holidayDates);
                leaveRequest(company, employee, paidTypes.get(random.nextInt(paidTypes.size())), start, start, 1,
                        "PENDING", null, "Personal work");
                leaveRequests++;
            }
            if (i % 13 == 4 && !pastWorkdays.isEmpty()) {
                LocalDate start = pastWorkdays.get(random.nextInt(pastWorkdays.size()));
                leaveRequest(company, employee, paidTypes.get(0), start, start, 1, "REJECTED",
                        employee.getReportingManager(), "Quarter-end release; please reschedule");
                leaveRequests++;
            }
        }
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        Employee lopEmployee = employees.get(9);
        LocalDate lopDay = nextWorkday(lastMonth.atDay(16), holidayDates);
        leaveRequest(company, lopEmployee, lop, lopDay, lopDay, 1, "APPROVED", lopEmployee.getReportingManager(), "Extended travel");
        onLeave.computeIfAbsent(lopEmployee.getId(), id -> new HashSet<>()).add(lopDay);
        leaveRequests++;

        // --- attendance: 3 months of web check-ins with late marks and overtime
        long attendanceRows = 0;
        for (Employee employee : employees) {
            Set<LocalDate> leaveDays = onLeave.getOrDefault(employee.getId(), Set.of());
            for (LocalDate day : pastWorkdays) {
                if (day.isBefore(employee.getDateOfJoining()) || leaveDays.contains(day)) continue;
                if (random.nextInt(60) == 0) continue; // occasional unexplained absence
                boolean late = random.nextInt(9) == 0;
                boolean overtime = random.nextInt(10) == 0;
                LocalDateTime in = day.atTime(late ? LocalTime.of(9, 50).plusMinutes(random.nextInt(40))
                        : LocalTime.of(9, 0).plusMinutes(random.nextInt(38)));
                LocalDateTime out = day.atTime(overtime ? LocalTime.of(20, 0).plusMinutes(random.nextInt(90))
                        : LocalTime.of(18, 0).plusMinutes(random.nextInt(45)));
                AttendanceSession session = new AttendanceSession();
                session.setCompany(company);
                session.setEmployee(employee);
                session.setAttendanceDate(day);
                session.setCheckInTime(in);
                session.setCheckOutTime(out);
                session.setStatus("CHECKED_OUT");
                session.setSource("WEB");
                session.setCheckInSourceDetails("WEB");
                session.setLocationType("OFFICE");
                session.setDurationMinutes(ChronoUnit.MINUTES.between(in, out));
                em.persist(session);
                attendanceRows++;
            }
        }

        // --- hiring pipeline
        Object[][] openings = {
                {"Senior Java Developer", "ENG", 2, "OPEN"}, {"React Frontend Engineer", "ENG", 1, "OPEN"},
                {"Inside Sales Executive", "SAL", 3, "OPEN"}, {"Customer Support Associate (Tamil/English)", "CS", 2, "IN_PROGRESS"}};
        String[] stages = {"APPLIED", "APPLIED", "SHORTLISTED", "ROUND1", "ROUND2", "OFFERED", "REJECTED", "HIRED"};
        int candidates = 0;
        int openingIndex = 0;
        for (Object[] row : openings) {
            JobOpening opening = new JobOpening();
            opening.setCompany(company);
            opening.setTitle((String) row[0]);
            opening.setDepartment(departments.get((String) row[1]));
            opening.setDesignation(designations.get((String) row[1])[2]);
            opening.setOpeningsCount((Integer) row[2]);
            opening.setStatus((String) row[3]);
            opening.setDescription("We are hiring a " + row[0] + " for our Chennai office.");
            opening.setPostedDate(today.minusDays(10 + 7L * openingIndex));
            opening.setRecruiter(heads.get("HR"));
            em.persist(opening);
            int count = openingIndex == 0 ? 6 : 4;
            for (int c = 0; c < count; c++) {
                boolean female = random.nextBoolean();
                String first = female ? FEMALE[random.nextInt(FEMALE.length)] : MALE[random.nextInt(MALE.length)];
                String last = LAST[random.nextInt(LAST.length)];
                Candidate candidate = new Candidate();
                candidate.setFirstName(first);
                candidate.setLastName(last);
                candidate.setEmail(first.toLowerCase(Locale.ROOT) + "." + last.toLowerCase(Locale.ROOT) + ".cand" + candidates
                        + "." + code.toLowerCase(Locale.ROOT) + "@" + EMAIL_DOMAIN);
                candidate.setPhone("+9197" + String.format("%08d", random.nextInt(99_999_999)));
                candidate.setJobOpening(opening);
                candidate.setSource(random.nextBoolean() ? "LinkedIn" : "Naukri");
                candidate.setExperienceYears((double) (1 + random.nextInt(8)));
                candidate.setSkills(openingIndex < 2 ? "Java, Spring Boot, React, SQL" : "Communication, CRM, Tamil, English");
                String stage = stages[(c + openingIndex) % stages.length];
                candidate.setStage(stage);
                candidate.setAppliedDate(today.minusDays(3 + random.nextInt(25)));
                candidate.setRating(2 + random.nextInt(4));
                if ("REJECTED".equals(stage)) candidate.setRejectionReason("Not a fit for the role");
                if ("OFFERED".equals(stage) || "HIRED".equals(stage)) {
                    candidate.setOfferAmount(600000d + random.nextInt(8) * 50000d);
                    candidate.setExpectedJoiningDate(today.plusDays(21));
                    candidate.setOfferGeneratedAt(LocalDateTime.now(IST).minusDays(2));
                }
                em.persist(candidate);
                candidates++;
            }
            openingIndex++;
        }

        // --- announcements (already "notified" so the dispatcher never fans them out)
        Object[][] announcements = {
                {"Welcome to Vettri HRMS", "All HR processes - leave, attendance, payroll and expenses - now live in Vettri.", "GENERAL", true},
                {"Updated leave policy for " + year, "Earned leave now carries forward up to 30 days. Read the policy and acknowledge.", "POLICY", false},
                {"Office closed on Deepavali", "Our Chennai office will be closed for Deepavali. Enjoy the festival with family!", "HOLIDAY", false},
                {"Server maintenance this Saturday", "Internal tools will be unavailable from 10 PM to 2 AM.", "URGENT", false}};
        for (Object[] row : announcements) {
            Announcement announcement = new Announcement();
            announcement.setCompany(company);
            announcement.setTitle((String) row[0]);
            announcement.setBody((String) row[1]);
            announcement.setCategory((String) row[2]);
            announcement.setPinned((Boolean) row[3]);
            announcement.setStatus(Announcement.STATUS_PUBLISHED);
            LocalDateTime published = LocalDateTime.now(IST).minusDays(1 + random.nextInt(20));
            announcement.setPublishAt(published);
            announcement.setPublishedAt(published);
            announcement.setNotifiedAt(published);
            announcement.setRequiresAcknowledgement("POLICY".equals(row[2]));
            if ("POLICY".equals(row[2])) announcement.setAcknowledgementDueDate(today.plusDays(7));
            announcement.setAuthorName("Priya Raman");
            em.persist(announcement);
        }

        // --- expense claims
        em.flush();
        expenseCategoryService.ensureDefaults(companyId);
        List<ExpenseCategory> categories = em.createQuery(
                "SELECT c FROM ExpenseCategory c WHERE c.company.id = :cid AND c.deleted = false ORDER BY c.sortOrder", ExpenseCategory.class)
                .setParameter("cid", companyId).getResultList();
        String[] claimStatuses = {ExpenseClaim.STATUS_SUBMITTED, ExpenseClaim.STATUS_SUBMITTED, ExpenseClaim.STATUS_SUBMITTED,
                ExpenseClaim.STATUS_MANAGER_APPROVED, ExpenseClaim.STATUS_REIMBURSED, ExpenseClaim.STATUS_REIMBURSED,
                ExpenseClaim.STATUS_REJECTED, ExpenseClaim.STATUS_DRAFT};
        String[] claimTitles = {"Client visit - Bengaluru", "Team lunch", "Cab to airport", "Conference registration",
                "Customer demo travel", "Internet reimbursement", "Hotel stay - Hyderabad", "Office supplies"};
        int claims = 0;
        for (int i = 0; i < claimStatuses.length && !categories.isEmpty(); i++) {
            Employee employee = employees.get(12 + i * 3);
            ExpenseClaim claim = new ExpenseClaim();
            claim.setCompany(company);
            claim.setEmployee(employee);
            claim.setTitle(claimTitles[i]);
            claim.setPurpose("Business expense");
            String status = claimStatuses[i];
            claim.setStatus(status);
            LocalDateTime submitted = LocalDateTime.now(IST).minusDays(2 + i * 3L);
            if (!ExpenseClaim.STATUS_DRAFT.equals(status)) claim.setSubmittedAt(submitted);
            if (ExpenseClaim.STATUS_MANAGER_APPROVED.equals(status) || ExpenseClaim.STATUS_REIMBURSED.equals(status)) {
                claim.setManagerApprovedBy(employee.getReportingManager());
                claim.setManagerApprovedAt(submitted.plusDays(1));
            }
            if (ExpenseClaim.STATUS_REIMBURSED.equals(status)) {
                claim.setApprovedBy(heads.get("FIN"));
                claim.setApprovedAt(submitted.plusDays(2));
                claim.setReimbursedAt(submitted.plusDays(3));
                claim.setReimbursementMethod(ExpenseClaim.METHOD_MANUAL);
                claim.setPaymentReference("NEFT" + (100000 + random.nextInt(899999)));
            }
            if (ExpenseClaim.STATUS_REJECTED.equals(status)) {
                claim.setRejectedBy(employee.getReportingManager());
                claim.setRejectedAt(submitted.plusDays(1));
                claim.setRejectionReason("Receipt missing");
                claim.setRejectedStage("MANAGER");
            }
            BigDecimal total = BigDecimal.ZERO;
            List<ExpenseClaimLine> lines = new ArrayList<>();
            int lineCount = 1 + random.nextInt(2);
            for (int l = 0; l < lineCount; l++) {
                ExpenseClaimLine line = new ExpenseClaimLine();
                line.setCompany(company);
                line.setClaim(claim);
                line.setCategory(categories.get(random.nextInt(categories.size())));
                line.setExpenseDate(submitted.toLocalDate().minusDays(1 + l));
                line.setDescription(claimTitles[i] + (l == 0 ? "" : " (part " + (l + 1) + ")"));
                BigDecimal amount = BigDecimal.valueOf(300 + random.nextInt(4500)).setScale(2, RoundingMode.HALF_UP);
                line.setAmount(amount);
                total = total.add(amount);
                lines.add(line);
            }
            claim.setTotalAmount(total);
            em.persist(claim);
            lines.forEach(em::persist);
            claims++;
        }

        // --- onboarding checklist in progress for the newest hire
        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setCompany(company);
        checklist.setEmployee(recentJoiner);
        checklist.setChecklistType(ChecklistConstants.TYPE_ONBOARDING);
        checklist.setName("New hire onboarding");
        checklist.setStatus(ChecklistConstants.STATUS_IN_PROGRESS);
        checklist.setAnchorDate(recentJoiner.getDateOfJoining());
        checklist.setStartedAt(recentJoiner.getDateOfJoining().atTime(10, 0));
        Object[][] tasks = {
                {"Sign offer letter and NDA", ChecklistConstants.OWNER_NEW_HIRE, true, -3},
                {"Submit PAN, Aadhaar and bank details", ChecklistConstants.OWNER_NEW_HIRE, true, 0},
                {"Create email and laptop", ChecklistConstants.OWNER_IT, true, 0},
                {"Add to PF and ESI", ChecklistConstants.OWNER_HR, false, 3},
                {"30-minute intro with manager", ChecklistConstants.OWNER_MANAGER, false, 2},
                {"Complete POSH awareness training", ChecklistConstants.OWNER_NEW_HIRE, false, 10}};
        int order = 0;
        for (Object[] row : tasks) {
            EmployeeChecklistTask task = new EmployeeChecklistTask();
            task.setCompany(company);
            task.setChecklist(checklist);
            task.setTitle((String) row[0]);
            task.setOwnerType((String) row[1]);
            task.setDueDate(recentJoiner.getDateOfJoining().plusDays((Integer) row[3]));
            boolean done = (Boolean) row[2];
            task.setStatus(done ? ChecklistConstants.TASK_DONE : ChecklistConstants.TASK_PENDING);
            if (done) {
                task.setCompletedAt(recentJoiner.getDateOfJoining().atTime(16, 0));
                task.setCompletedBy("Priya Raman");
            }
            task.setSortOrder(order++);
            checklist.getTasks().add(task);
        }
        em.persist(checklist);

        // --- a couple of custom fields so the profile shows them
        CustomFieldDefinition bloodGroup = customField(company, "blood_group", "Blood group", CustomFieldType.SELECT,
                List.of("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"), 0);
        CustomFieldDefinition tshirt = customField(company, "t_shirt_size", "T-shirt size", CustomFieldType.SELECT,
                List.of("S", "M", "L", "XL", "XXL"), 1);
        for (Employee employee : employees) {
            customValue(company, employee, bloodGroup, List.of("A+", "B+", "O+", "AB+", "O-").get(random.nextInt(5)));
            customValue(company, employee, tshirt, List.of("S", "M", "L", "XL").get(random.nextInt(4)));
        }

        em.flush();

        // --- payroll for the two completed months, processed through PayrollService
        int payrollRuns = runPayroll(company, adminUser, List.of(YearMonth.from(today).minusMonths(2), lastMonth));

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("departments", (long) departments.size());
        counts.put("employees", (long) employees.size());
        counts.put("attendanceSessions", attendanceRows);
        counts.put("leaveRequests", (long) leaveRequests);
        counts.put("holidays", (long) holidays.length);
        counts.put("payrollRuns", (long) payrollRuns);
        counts.put("jobOpenings", (long) openings.length);
        counts.put("candidates", (long) candidates);
        counts.put("announcements", (long) announcements.length);
        counts.put("expenseClaims", (long) claims);
        counts.put("onboardingChecklists", 1L);
        log.info("Seeded demo company {}: {}", companyId, counts);
        return new DemoCredentials(companyId, company.getName(), adminUser.getUsername(), adminPassword, counts);
    }

    private int runPayroll(Company company, User admin, List<YearMonth> months) {
        Long previousTenant = TenantContext.getCurrentTenant();
        SecurityContext previousContext = SecurityContextHolder.getContext();
        CustomUserPrincipal principal = new CustomUserPrincipal(admin);
        // Seeding is only reachable by SUPER_ADMIN (DemoCompanyController), so the internal payroll runs use
        // platform-admin rights scoped to the demo tenant rather than depending on the seeded role's scopes.
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>(principal.getAuthorities());
        authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        SecurityContext context = new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        int runs = 0;
        try {
            SecurityContextHolder.setContext(context);
            TenantContext.setCurrentTenant(company.getId());
            for (YearMonth month : months) {
                CreatePayrollRunRequest request = new CreatePayrollRunRequest();
                request.setPeriodMonth(month.getMonthValue());
                request.setPeriodYear(month.getYear());
                request.setPayDate(month.atEndOfMonth());
                request.setRemarks("Demo payroll");
                PayrollRunDTO run = payrollService.createRun(request);
                payrollService.process(run.getRun().getId());
                runs++;
            }
        } finally {
            SecurityContextHolder.setContext(previousContext);
            if (previousTenant == null) TenantContext.clear();
            else TenantContext.setCurrentTenant(previousTenant);
        }
        return runs;
    }

    // ------------------------------------------------------------------ helpers

    private void ensureSubscription(Company company, LocalDate today) {
        Long existing = em.createQuery("SELECT COUNT(s) FROM Subscription s WHERE s.company.id = :cid AND s.deleted = false", Long.class)
                .setParameter("cid", company.getId()).getSingleResult();
        if (existing > 0) return;
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(Plan.BUSINESS);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEmployeeLimit(100);
        subscription.setDeviceLimit(100);
        subscription.setBillingCycle("YEARLY");
        subscription.setBillableEmployeeCount(0);
        subscription.setStartDate(today);
        subscription.setRenewalDate(today.plusYears(5));
        subscription.setAmount(BigDecimal.ZERO);
        subscription.setRate(BigDecimal.ZERO);
        em.persist(subscription);
    }

    private void leaveRequest(Company company, Employee employee, LeaveType type, LocalDate start, LocalDate end,
                              double days, String status, Employee decidedBy, String reason) {
        LeaveRequest request = new LeaveRequest();
        request.setCompany(company);
        request.setEmployee(employee);
        request.setLeaveType(type);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setDays(days);
        request.setReason(reason);
        request.setStatus(status);
        if (!"PENDING".equals(status)) {
            request.setDecidedBy(decidedBy);
            request.setDecidedAt(start.minusDays(2).atTime(11, 0));
            request.setDecisionNote("APPROVED".equals(status) ? "Approved" : reason);
            if ("REJECTED".equals(status)) request.setReason("Personal work");
        }
        em.persist(request);
    }

    private CustomFieldDefinition customField(Company company, String key, String label, CustomFieldType type,
                                              List<String> options, int order) {
        CustomFieldDefinition definition = new CustomFieldDefinition();
        definition.setCompany(company);
        definition.setFieldKey(key);
        definition.setLabel(label);
        definition.setFieldType(type);
        definition.setOptions(CustomFieldValidator.writeOptions(options));
        definition.setSection("Personal");
        definition.setVisibleToEmployee(true);
        definition.setEditableByEmployee(true);
        definition.setSortOrder(order);
        em.persist(definition);
        return definition;
    }

    private void customValue(Company company, Employee employee, CustomFieldDefinition definition, String value) {
        EmployeeCustomFieldValue row = new EmployeeCustomFieldValue();
        row.setCompany(company);
        row.setEmployee(employee);
        row.setDefinition(definition);
        row.setValueText(value);
        em.persist(row);
    }

    private static List<LocalDate> workdays(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (isWorkday(day, holidays)) days.add(day);
        }
        return days;
    }

    private static LocalDate nextWorkday(LocalDate day, Set<LocalDate> holidays) {
        LocalDate next = day.plusDays(1);
        while (!isWorkday(next, holidays)) next = next.plusDays(1);
        return next;
    }

    private static boolean isWorkday(LocalDate day, Set<LocalDate> holidays) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day);
    }

    private static String emailFor(String first, String last, int seq, String code) {
        return (first + "." + last).toLowerCase(Locale.ROOT) + "." + seq + "." + code.toLowerCase(Locale.ROOT) + "@" + EMAIL_DOMAIN;
    }

    private static String pan(Random random, String last) {
        StringBuilder pan = new StringBuilder();
        for (int i = 0; i < 3; i++) pan.append((char) ('A' + random.nextInt(26)));
        pan.append('P').append(Character.toUpperCase(last.charAt(0)));
        pan.append(String.format("%04d", random.nextInt(10000)));
        pan.append((char) ('A' + random.nextInt(26)));
        return pan.toString();
    }

    private long countEmployees(Long companyId) {
        return em.createQuery("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :cid AND e.deleted = false", Long.class)
                .setParameter("cid", companyId).getSingleResult();
    }

    /** Short base-36 tag making codes/emails/usernames unique per seed. */
    private String generationTag() {
        // Millisecond clock (wraps after ~70 years in 7 base-36 chars) plus an in-process counter.
        String time = Long.toString(System.currentTimeMillis(), 36);
        String counter = Integer.toString(SEED_COUNTER.getAndIncrement() % 36, 36);
        return (time.substring(Math.max(0, time.length() - 6)) + counter).toLowerCase(Locale.ROOT);
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEED_COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    private String randomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String symbols = "!@#$%*?";
        String all = upper + lower + digits + symbols;
        StringBuilder password = new StringBuilder();
        password.append(upper.charAt(secureRandom.nextInt(upper.length())));
        password.append(lower.charAt(secureRandom.nextInt(lower.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(symbols.charAt(secureRandom.nextInt(symbols.length())));
        for (int i = 0; i < 10; i++) password.append(all.charAt(secureRandom.nextInt(all.length())));
        return password.toString();
    }
}
