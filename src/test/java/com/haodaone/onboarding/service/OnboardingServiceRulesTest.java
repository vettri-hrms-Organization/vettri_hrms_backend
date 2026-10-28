package com.haodaone.onboarding.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.onboarding.entity.ChecklistConstants;
import com.haodaone.onboarding.entity.ChecklistTemplate;
import com.haodaone.onboarding.entity.ChecklistTemplateTask;
import com.haodaone.onboarding.entity.EmployeeChecklist;
import com.haodaone.onboarding.entity.EmployeeChecklistTask;
import com.haodaone.onboarding.repository.ChecklistTemplateRepository;
import com.haodaone.onboarding.repository.EmployeeChecklistRepository;
import com.haodaone.onboarding.repository.EmployeeChecklistTaskRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingServiceRulesTest {

    private static Employee employee(long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName("E" + id);
        e.setLastName("Test");
        return e;
    }

    private static User user(long id, String... roles) {
        User u = new User();
        u.setId(id);
        java.util.Set<Role> set = new java.util.HashSet<>();
        for (String r : roles) {
            Role role = new Role();
            role.setName(r);
            set.add(role);
        }
        u.setRoles(set);
        return u;
    }

    private static EmployeeChecklistTask task(String ownerType, Employee hire, Employee ownerEmployee, User ownerUser) {
        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setEmployee(hire);
        checklist.setStatus(ChecklistConstants.STATUS_IN_PROGRESS);
        EmployeeChecklistTask t = new EmployeeChecklistTask();
        t.setChecklist(checklist);
        t.setOwnerType(ownerType);
        t.setOwnerEmployee(ownerEmployee);
        t.setOwnerUser(ownerUser);
        return t;
    }

    @Test
    void ownershipRules() {
        Employee hire = employee(1);
        Employee manager = employee(2);
        Employee stranger = employee(3);
        User itUser = user(10, "IT_ADMINISTRATOR");
        User plainUser = user(11, "EMPLOYEE");

        var hireViewer = new OnboardingService.Viewer(false, user(20, "EMPLOYEE"), hire);
        var managerViewer = new OnboardingService.Viewer(false, user(21, "MANAGER"), manager);
        var strangerViewer = new OnboardingService.Viewer(false, plainUser, stranger);
        var itViewer = new OnboardingService.Viewer(false, itUser, null);
        var hrViewer = new OnboardingService.Viewer(true, user(30, "HR_ADMIN"), null);

        EmployeeChecklistTask newHireTask = task(ChecklistConstants.OWNER_NEW_HIRE, hire, hire, null);
        assertTrue(OnboardingService.canComplete(newHireTask, hireViewer));
        assertFalse(OnboardingService.canComplete(newHireTask, strangerViewer));
        assertTrue(OnboardingService.canComplete(newHireTask, hrViewer));

        EmployeeChecklistTask managerTask = task(ChecklistConstants.OWNER_MANAGER, hire, manager, null);
        assertTrue(OnboardingService.canComplete(managerTask, managerViewer));
        assertFalse(OnboardingService.canComplete(managerTask, hireViewer));

        EmployeeChecklistTask itTask = task(ChecklistConstants.OWNER_IT, hire, null, null);
        assertTrue(OnboardingService.canComplete(itTask, itViewer));
        assertFalse(OnboardingService.canComplete(itTask, strangerViewer));

        EmployeeChecklistTask userTask = task(ChecklistConstants.OWNER_USER, hire, null, plainUser);
        assertTrue(OnboardingService.canComplete(userTask, strangerViewer));
        assertFalse(OnboardingService.canComplete(userTask, itViewer));

        EmployeeChecklistTask hrTask = task(ChecklistConstants.OWNER_HR, hire, null, null);
        assertFalse(OnboardingService.canComplete(hrTask, managerViewer));
        assertTrue(OnboardingService.canComplete(hrTask, hrViewer));
    }

    @Test
    void overdueOnlyForPendingTasksPastDue() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        EmployeeChecklistTask t = task(ChecklistConstants.OWNER_HR, employee(1), null, null);
        t.setDueDate(today.minusDays(1));
        assertTrue(OnboardingService.isOverdue(t, today));
        t.setDueDate(today);
        assertFalse(OnboardingService.isOverdue(t, today));
        t.setDueDate(today.minusDays(5));
        t.setStatus(ChecklistConstants.TASK_DONE);
        assertFalse(OnboardingService.isOverdue(t, today));
    }

    @Test
    void instantiateComputesDueDatesFromJoiningDateAndResolvesOwners() {
        EmployeeChecklistRepository checklistRepository = mock(EmployeeChecklistRepository.class);
        when(checklistRepository.save(any(EmployeeChecklist.class))).thenAnswer(inv -> {
            EmployeeChecklist c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
        OnboardingService service = new OnboardingService(mock(ChecklistTemplateRepository.class), checklistRepository,
                mock(EmployeeChecklistTaskRepository.class), mock(CompanyRepository.class), mock(EmployeeRepository.class),
                mock(UserRepository.class), mock(EmployeeDocumentRepository.class), mock(EmployeeDocumentS3StorageService.class),
                mock(NotificationService.class), mock(AuthorizationService.class), mock(AuditLogService.class));

        Company company = new Company();
        company.setId(5L);
        Employee manager = employee(2);
        Employee hire = employee(1);
        hire.setCompany(company);
        hire.setReportingManager(manager);
        hire.setDateOfJoining(LocalDate.of(2026, 11, 2));

        ChecklistTemplate template = new ChecklistTemplate();
        template.setName("Standard onboarding");
        template.setTemplateType(ChecklistConstants.TYPE_ONBOARDING);
        template.getTasks().add(templateTask(template, "ID proof", ChecklistConstants.OWNER_NEW_HIRE, -3, 0));
        template.getTasks().add(templateTask(template, "Buddy", ChecklistConstants.OWNER_MANAGER, 0, 1));
        template.getTasks().add(templateTask(template, "30-day", ChecklistConstants.OWNER_MANAGER, 30, 2));

        EmployeeChecklist checklist = service.instantiate(template, hire, null);

        assertEquals(LocalDate.of(2026, 11, 2), checklist.getAnchorDate());
        List<EmployeeChecklistTask> tasks = checklist.getTasks();
        assertEquals(3, tasks.size());
        assertEquals(LocalDate.of(2026, 10, 30), tasks.get(0).getDueDate());
        assertSame(hire, tasks.get(0).getOwnerEmployee());
        assertSame(manager, tasks.get(1).getOwnerEmployee());
        assertEquals(LocalDate.of(2026, 12, 2), tasks.get(2).getDueDate());
        assertTrue(tasks.stream().allMatch(t -> ChecklistConstants.TASK_PENDING.equals(t.getStatus())));
        verify(checklistRepository).save(any(EmployeeChecklist.class));

        var dto = service.toChecklistDTO(checklist, new OnboardingService.Viewer(false, null, hire));
        assertEquals(0, dto.progressPercent());
        assertEquals(Set.of(true, false), Set.copyOf(dto.tasks().stream().map(t -> t.canComplete()).toList()));
    }

    private static ChecklistTemplateTask templateTask(ChecklistTemplate template, String title, String owner, int offset, int order) {
        ChecklistTemplateTask t = new ChecklistTemplateTask();
        t.setTemplate(template);
        t.setTitle(title);
        t.setOwnerType(owner);
        t.setDueOffsetDays(offset);
        t.setSortOrder(order);
        return t;
    }
}
