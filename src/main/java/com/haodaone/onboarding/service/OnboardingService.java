package com.haodaone.onboarding.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.onboarding.dto.OnboardingDtos;
import com.haodaone.onboarding.entity.ChecklistConstants;
import com.haodaone.onboarding.entity.ChecklistTemplate;
import com.haodaone.onboarding.entity.ChecklistTemplateTask;
import com.haodaone.onboarding.entity.EmployeeChecklist;
import com.haodaone.onboarding.entity.EmployeeChecklistTask;
import com.haodaone.onboarding.repository.ChecklistTemplateRepository;
import com.haodaone.onboarding.repository.EmployeeChecklistRepository;
import com.haodaone.onboarding.repository.EmployeeChecklistTaskRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Onboarding / offboarding checklist engine: per-company templates (with
 * built-in defaults seeded on first use), checklists started automatically
 * on employee creation / exit or manually by HR, task ownership and
 * completion rules, and daily overdue reminders.
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String MANAGE_PERMISSION = "ONBOARDING_MANAGE";

    private final ChecklistTemplateRepository templateRepository;
    private final EmployeeChecklistRepository checklistRepository;
    private final EmployeeChecklistTaskRepository taskRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeDocumentS3StorageService documentStorage;
    private final NotificationService notificationService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public OnboardingService(ChecklistTemplateRepository templateRepository, EmployeeChecklistRepository checklistRepository,
                             EmployeeChecklistTaskRepository taskRepository, CompanyRepository companyRepository,
                             EmployeeRepository employeeRepository, UserRepository userRepository,
                             EmployeeDocumentRepository documentRepository, EmployeeDocumentS3StorageService documentStorage,
                             NotificationService notificationService, AuthorizationService authorizationService,
                             AuditLogService auditLogService) {
        this.templateRepository = templateRepository;
        this.checklistRepository = checklistRepository;
        this.taskRepository = taskRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.notificationService = notificationService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    // ================================================================== templates

    /** Seeds the default ONBOARDING and EXIT templates for a company if it has none of that type. Idempotent. */
    @Transactional
    public void ensureDefaults(Long companyId) {
        seedIfMissing(companyId, DefaultChecklistTemplates.ONBOARDING);
        seedIfMissing(companyId, DefaultChecklistTemplates.EXIT);
    }

    private void seedIfMissing(Long companyId, DefaultChecklistTemplates.TemplateDef def) {
        if (templateRepository.existsByCompany_IdAndTemplateType(companyId, def.type())) return;
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));
        ChecklistTemplate template = new ChecklistTemplate();
        template.setCompany(company);
        template.setName(def.name());
        template.setTemplateType(def.type());
        template.setDescription(def.description());
        template.setDefaultTemplate(true);
        template.setAutoStart(true);
        template.setActive(true);
        int order = 0;
        for (DefaultChecklistTemplates.TaskDef d : def.tasks()) {
            ChecklistTemplateTask task = new ChecklistTemplateTask();
            task.setCompany(company);
            task.setTemplate(template);
            task.setTitle(d.title());
            task.setDescription(d.description());
            task.setOwnerType(d.ownerType());
            task.setDueOffsetDays(d.dueOffsetDays());
            task.setRequiresDocument(d.requiresDocument());
            task.setDocumentType(d.documentType());
            task.setLinkKey(d.linkKey());
            task.setSortOrder(order++);
            template.getTasks().add(task);
        }
        templateRepository.save(template);
    }

    @Transactional
    public List<OnboardingDtos.TemplateDTO> listTemplates() {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        return templateRepository.findAllByCompany_IdAndDeletedFalseOrderByTemplateTypeAscNameAsc(companyId).stream()
                .map(this::toTemplateDTO).toList();
    }

    @Transactional
    public OnboardingDtos.TemplateDTO createTemplate(OnboardingDtos.TemplateRequest request) {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        ChecklistTemplate template = new ChecklistTemplate();
        template.setCompany(companyRepository.getReferenceById(companyId));
        applyTemplate(template, request, companyId);
        ChecklistTemplate saved = templateRepository.save(template);
        enforceSingleDefault(saved, companyId);
        auditLogService.log("ChecklistTemplate", saved.getId(), "CREATE", "Checklist template '" + saved.getName() + "' created");
        return toTemplateDTO(saved);
    }

    @Transactional
    public OnboardingDtos.TemplateDTO updateTemplate(Long id, OnboardingDtos.TemplateRequest request) {
        Long companyId = requiredTenant();
        ChecklistTemplate template = findTemplate(id, companyId);
        applyTemplate(template, request, companyId);
        ChecklistTemplate saved = templateRepository.save(template);
        enforceSingleDefault(saved, companyId);
        auditLogService.log("ChecklistTemplate", saved.getId(), "UPDATE", "Checklist template '" + saved.getName() + "' updated");
        return toTemplateDTO(saved);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        Long companyId = requiredTenant();
        ChecklistTemplate template = findTemplate(id, companyId);
        template.setDeleted(true);
        template.setDeletedAt(LocalDateTime.now());
        template.setDefaultTemplate(false);
        templateRepository.save(template);
        auditLogService.log("ChecklistTemplate", id, "DELETE", "Checklist template '" + template.getName() + "' deleted");
    }

    private void applyTemplate(ChecklistTemplate template, OnboardingDtos.TemplateRequest request, Long companyId) {
        if (request == null) throw new BadRequestException("Template details are required.");
        if (request.name() == null || request.name().isBlank()) throw new BadRequestException("Template name is required.");
        String type = request.templateType() == null ? ChecklistConstants.TYPE_ONBOARDING
                : request.templateType().trim().toUpperCase(Locale.ROOT);
        if (!ChecklistConstants.TYPES.contains(type)) {
            throw new BadRequestException("Template type must be ONBOARDING or EXIT.");
        }
        List<OnboardingDtos.TemplateTaskRequest> taskRequests = request.tasks() == null ? List.of() : request.tasks();
        if (taskRequests.isEmpty()) throw new BadRequestException("Add at least one task to the template.");
        template.setName(trimTo(request.name(), 150));
        template.setTemplateType(type);
        template.setDescription(trimTo(request.description(), 1000));
        if (request.defaultTemplate() != null) template.setDefaultTemplate(request.defaultTemplate());
        if (request.autoStart() != null) template.setAutoStart(request.autoStart());
        if (request.active() != null) template.setActive(request.active());

        template.getTasks().clear();
        int order = 0;
        for (OnboardingDtos.TemplateTaskRequest r : taskRequests) {
            if (r.title() == null || r.title().isBlank()) throw new BadRequestException("Every task needs a title.");
            String owner = r.ownerType() == null ? "" : r.ownerType().trim().toUpperCase(Locale.ROOT);
            if (!ChecklistConstants.OWNER_TYPES.contains(owner)) {
                throw new BadRequestException("Task '" + r.title() + "': owner must be one of " + ChecklistConstants.OWNER_TYPES);
            }
            User ownerUser = null;
            if (r.ownerUserId() != null) {
                ownerUser = userRepository.findByIdAndCompanyIdAndDeletedFalse(r.ownerUserId(), companyId)
                        .orElseThrow(() -> new BadRequestException("Unknown user for task '" + r.title() + "'."));
            }
            if (ChecklistConstants.OWNER_USER.equals(owner) && ownerUser == null) {
                throw new BadRequestException("Task '" + r.title() + "': choose the user who owns it.");
            }
            int offset = r.dueOffsetDays() == null ? 0 : r.dueOffsetDays();
            if (Math.abs(offset) > 365) throw new BadRequestException("Task '" + r.title() + "': due offset must be within 365 days.");
            ChecklistTemplateTask task = new ChecklistTemplateTask();
            task.setCompany(template.getCompany());
            task.setTemplate(template);
            task.setTitle(trimTo(r.title(), 200));
            task.setDescription(trimTo(r.description(), 1000));
            task.setOwnerType(owner);
            task.setOwnerUser(ChecklistConstants.OWNER_USER.equals(owner) || ChecklistConstants.OWNER_IT.equals(owner) ? ownerUser : null);
            task.setDueOffsetDays(offset);
            task.setRequiresDocument(Boolean.TRUE.equals(r.requiresDocument()));
            task.setDocumentType(Boolean.TRUE.equals(r.requiresDocument())
                    ? (r.documentType() == null || r.documentType().isBlank() ? "OTHER" : trimTo(r.documentType().toUpperCase(Locale.ROOT), 40))
                    : null);
            task.setLinkKey(trimTo(r.linkKey(), 40));
            task.setSortOrder(order++);
            template.getTasks().add(task);
        }
    }

    private void enforceSingleDefault(ChecklistTemplate saved, Long companyId) {
        if (!saved.isDefaultTemplate()) return;
        for (ChecklistTemplate other : templateRepository.findAllByCompany_IdAndDeletedFalseOrderByTemplateTypeAscNameAsc(companyId)) {
            if (!other.getId().equals(saved.getId()) && other.isDefaultTemplate()
                    && other.getTemplateType().equals(saved.getTemplateType())) {
                other.setDefaultTemplate(false);
                templateRepository.save(other);
            }
        }
    }

    private OnboardingDtos.TemplateDTO toTemplateDTO(ChecklistTemplate t) {
        List<OnboardingDtos.TemplateTaskDTO> tasks = t.getTasks().stream()
                .filter(task -> !task.isDeleted())
                .sorted(Comparator.comparingInt(ChecklistTemplateTask::getSortOrder))
                .map(task -> new OnboardingDtos.TemplateTaskDTO(task.getId(), task.getTitle(), task.getDescription(),
                        task.getOwnerType(), task.getOwnerUser() != null ? task.getOwnerUser().getId() : null,
                        task.getOwnerUser() != null ? displayName(task.getOwnerUser()) : null,
                        task.getDueOffsetDays(), task.isRequiresDocument(), task.getDocumentType(), task.getLinkKey(),
                        task.getSortOrder()))
                .toList();
        return new OnboardingDtos.TemplateDTO(t.getId(), t.getName(), t.getTemplateType(), t.getDescription(),
                t.isDefaultTemplate(), t.isAutoStart(), t.isActive(), tasks);
    }

    // ================================================================== starting checklists

    /** HR starts a checklist manually from the employee profile. */
    @Transactional
    public OnboardingDtos.ChecklistDTO start(OnboardingDtos.StartRequest request) {
        Long companyId = requiredTenant();
        if (request == null || request.employeeId() == null) throw new BadRequestException("Choose an employee.");
        ensureDefaults(companyId);
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.employeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.employeeId()));
        ChecklistTemplate template;
        if (request.templateId() != null) {
            template = findTemplate(request.templateId(), companyId);
        } else {
            String type = request.checklistType() == null ? ChecklistConstants.TYPE_ONBOARDING
                    : request.checklistType().trim().toUpperCase(Locale.ROOT);
            template = templateRepository.findFirstByCompany_IdAndTemplateTypeAndDefaultTemplateTrueAndActiveTrueAndDeletedFalseOrderByIdAsc(companyId, type)
                    .orElseThrow(() -> new BadRequestException("No active default " + type.toLowerCase(Locale.ROOT) + " template - pick a template."));
        }
        if (!template.isActive()) throw new BadRequestException("Template '" + template.getName() + "' is inactive.");
        if (checklistRepository.existsByCompany_IdAndEmployee_IdAndChecklistTypeAndStatusAndDeletedFalse(
                companyId, employee.getId(), template.getTemplateType(), ChecklistConstants.STATUS_IN_PROGRESS)) {
            throw new BadRequestException(employee.getFullName() + " already has an " + template.getTemplateType().toLowerCase(Locale.ROOT)
                    + " checklist in progress.");
        }
        EmployeeChecklist checklist = instantiate(template, employee, request.anchorDate());
        return toChecklistDTO(checklist, viewer());
    }

    /** Event hook: auto-start the default onboarding checklist for a newly created employee. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoStart(Long companyId, Long employeeId, String type, LocalDate anchorDate) {
        ensureDefaults(companyId);
        ChecklistTemplate template = templateRepository
                .findFirstByCompany_IdAndTemplateTypeAndDefaultTemplateTrueAndActiveTrueAndDeletedFalseOrderByIdAsc(companyId, type)
                .orElse(null);
        if (template == null || !template.isAutoStart()) return;
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId).orElse(null);
        if (employee == null) return;
        if (checklistRepository.existsByCompany_IdAndEmployee_IdAndChecklistTypeAndStatusAndDeletedFalse(
                companyId, employeeId, type, ChecklistConstants.STATUS_IN_PROGRESS)) return;
        instantiate(template, employee, anchorDate);
    }

    EmployeeChecklist instantiate(ChecklistTemplate template, Employee employee, LocalDate requestedAnchor) {
        boolean exit = ChecklistConstants.TYPE_EXIT.equals(template.getTemplateType());
        LocalDate today = LocalDate.now(IST);
        LocalDate anchor = requestedAnchor != null ? requestedAnchor
                : exit ? (employee.getLastWorkingDate() != null ? employee.getLastWorkingDate() : today)
                : (employee.getDateOfJoining() != null ? employee.getDateOfJoining() : today);

        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setCompany(employee.getCompany());
        checklist.setEmployee(employee);
        checklist.setTemplate(template);
        checklist.setChecklistType(template.getTemplateType());
        checklist.setName(template.getName());
        checklist.setStatus(ChecklistConstants.STATUS_IN_PROGRESS);
        checklist.setAnchorDate(anchor);
        checklist.setStartedAt(LocalDateTime.now());
        for (ChecklistTemplateTask def : template.getTasks()) {
            if (def.isDeleted()) continue;
            EmployeeChecklistTask task = new EmployeeChecklistTask();
            task.setCompany(employee.getCompany());
            task.setChecklist(checklist);
            task.setTitle(def.getTitle());
            task.setDescription(def.getDescription());
            task.setOwnerType(def.getOwnerType());
            task.setOwnerUser(def.getOwnerUser());
            if (ChecklistConstants.OWNER_NEW_HIRE.equals(def.getOwnerType())) {
                task.setOwnerEmployee(employee);
            } else if (ChecklistConstants.OWNER_MANAGER.equals(def.getOwnerType())) {
                task.setOwnerEmployee(employee.getReportingManager());
            }
            task.setDueDate(anchor.plusDays(def.getDueOffsetDays()));
            task.setRequiresDocument(def.isRequiresDocument());
            task.setDocumentType(def.getDocumentType());
            task.setLinkKey(def.getLinkKey());
            task.setSortOrder(def.getSortOrder());
            checklist.getTasks().add(task);
        }
        EmployeeChecklist saved = checklistRepository.save(checklist);
        auditLogService.log("EmployeeChecklist", saved.getId(), "START",
                "Started '" + saved.getName() + "' for '" + employee.getFullName() + "'");
        notifyStart(saved);
        return saved;
    }

    private void notifyStart(EmployeeChecklist checklist) {
        try {
            Long companyId = checklist.getCompany().getId();
            Employee employee = checklist.getEmployee();
            boolean exit = ChecklistConstants.TYPE_EXIT.equals(checklist.getChecklistType());
            Map<Long, Integer> counts = new LinkedHashMap<>();
            boolean hasHrTasks = false;
            for (EmployeeChecklistTask task : checklist.getTasks()) {
                if (ChecklistConstants.OWNER_HR.equals(task.getOwnerType())) {
                    hasHrTasks = true;
                    continue;
                }
                for (Long userId : recipientUserIds(task, companyId)) counts.merge(userId, 1, Integer::sum);
            }
            String label = exit ? "offboarding" : "onboarding";
            counts.forEach((userId, n) -> notificationService.notifyUser(companyId, userId, "ONBOARDING_TASK",
                    "New " + label + " tasks",
                    "You have " + n + " " + label + " task" + (n == 1 ? "" : "s") + " for " + employee.getFullName() + ".",
                    "EmployeeChecklist", checklist.getId(), "NORMAL"));
            if (hasHrTasks) {
                notificationService.notifyPermissionRecipients(companyId, employee.getId(), MANAGE_PERMISSION, "ONBOARDING_TASK",
                        (exit ? "Offboarding" : "Onboarding") + " started",
                        "'" + checklist.getName() + "' started for " + employee.getFullName() + ".",
                        "EmployeeChecklist", checklist.getId());
            }
        } catch (RuntimeException e) {
            log.warn("Could not send checklist start notifications for checklist {}: {}", checklist.getId(), e.getMessage());
        }
    }

    /** Users who should hear about a (non-HR) task. */
    private Set<Long> recipientUserIds(EmployeeChecklistTask task, Long companyId) {
        Set<Long> ids = new LinkedHashSet<>();
        switch (task.getOwnerType()) {
            case ChecklistConstants.OWNER_NEW_HIRE, ChecklistConstants.OWNER_MANAGER -> {
                Employee owner = task.getOwnerEmployee();
                if (owner != null && owner.getUser() != null) ids.add(owner.getUser().getId());
            }
            case ChecklistConstants.OWNER_USER -> {
                if (task.getOwnerUser() != null) ids.add(task.getOwnerUser().getId());
            }
            case ChecklistConstants.OWNER_IT -> {
                if (task.getOwnerUser() != null) ids.add(task.getOwnerUser().getId());
                else ids.addAll(itUserIds(companyId));
            }
            default -> { }
        }
        return ids;
    }

    private List<Long> itUserIds(Long companyId) {
        return userRepository.findAllByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(User::isActive)
                .filter(u -> u.getRoles().stream().anyMatch(r -> ChecklistConstants.IT_ROLE.equals(r.getName())))
                .map(User::getId)
                .toList();
    }

    // ================================================================== reading

    @Transactional(readOnly = true)
    public List<OnboardingDtos.ChecklistDTO> forEmployee(Long employeeId) {
        Long companyId = requiredTenant();
        Viewer viewer = viewer();
        return checklistRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByStartedAtDesc(companyId, employeeId)
                .stream().map(c -> toChecklistDTO(c, viewer)).toList();
    }

    /** The current user's own checklists (as the new hire / exiting employee). */
    @Transactional(readOnly = true)
    public List<OnboardingDtos.ChecklistDTO> mine() {
        Viewer viewer = viewer();
        if (viewer.employee() == null) return List.of();
        return checklistRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByStartedAtDesc(
                        requiredTenant(), viewer.employee().getId())
                .stream().filter(c -> !ChecklistConstants.STATUS_CANCELLED.equals(c.getStatus()))
                .map(c -> toChecklistDTO(c, viewer)).toList();
    }

    /** Open tasks on other people's checklists that the current user owns (manager, IT, named user). */
    @Transactional(readOnly = true)
    public List<OnboardingDtos.TaskDTO> myTasks() {
        Long companyId = requiredTenant();
        Viewer viewer = viewer();
        List<OnboardingDtos.TaskDTO> result = new ArrayList<>();
        for (EmployeeChecklistTask task : taskRepository.findOpenByCompany(companyId)) {
            if (isPersonalOwner(task, viewer) && !isOwnChecklist(task, viewer)) result.add(toTaskDTO(task, viewer));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public OnboardingDtos.DashboardDTO dashboard() {
        Long companyId = requiredTenant();
        Viewer viewer = viewer();
        LocalDate today = LocalDate.now(IST);
        List<OnboardingDtos.ChecklistDTO> checklists = checklistRepository
                .findAllByCompany_IdAndStatusAndDeletedFalseOrderByStartedAtDesc(companyId, ChecklistConstants.STATUS_IN_PROGRESS)
                .stream().map(c -> toChecklistDTO(c, viewer)).toList();
        List<OnboardingDtos.TaskDTO> overdue = new ArrayList<>();
        int dueThisWeek = 0;
        for (EmployeeChecklistTask task : taskRepository.findOpenByCompany(companyId)) {
            if (task.getDueDate() == null) continue;
            if (task.getDueDate().isBefore(today)) overdue.add(toTaskDTO(task, viewer));
            else if (!task.getDueDate().isAfter(today.plusDays(7))) dueThisWeek++;
        }
        return new OnboardingDtos.DashboardDTO(checklists.size(), overdue.size(), dueThisWeek, checklists, overdue);
    }

    /** For @PreAuthorize on the employee checklist endpoint. */
    @Transactional(readOnly = true)
    public Long resolveEmployeeIdForChecklist(Long checklistId) {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null || checklistId == null) return null;
        return checklistRepository.findByIdAndCompany_IdAndDeletedFalse(checklistId, tenant)
                .map(c -> c.getEmployee().getId()).orElse(null);
    }

    // ================================================================== completing

    @Transactional
    public OnboardingDtos.TaskDTO complete(Long taskId, OnboardingDtos.CompleteRequest request) {
        Long companyId = requiredTenant();
        EmployeeChecklistTask task = findTask(taskId, companyId);
        Viewer viewer = viewer();
        requireCanComplete(task, viewer);
        if (!ChecklistConstants.STATUS_IN_PROGRESS.equals(task.getChecklist().getStatus())) {
            throw new BadRequestException("This checklist is no longer in progress.");
        }
        boolean skip = request != null && Boolean.TRUE.equals(request.skip());
        if (skip && !viewer.hr()) throw new BadRequestException("Only HR can skip a checklist task.");
        if (!skip && task.isRequiresDocument() && task.getDocumentId() == null) {
            throw new BadRequestException("Upload the required document before completing '" + task.getTitle() + "'.");
        }
        task.setStatus(skip ? ChecklistConstants.TASK_SKIPPED : ChecklistConstants.TASK_DONE);
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(viewer.displayName());
        if (request != null && request.remarks() != null) task.setRemarks(trimTo(request.remarks(), 1000));
        taskRepository.save(task);
        refreshChecklistStatus(task.getChecklist());
        auditLogService.log("EmployeeChecklistTask", task.getId(), skip ? "SKIP" : "COMPLETE",
                "'" + task.getTitle() + "' " + (skip ? "skipped" : "completed") + " for '" + task.getChecklist().getEmployee().getFullName() + "'");
        return toTaskDTO(task, viewer);
    }

    @Transactional
    public OnboardingDtos.TaskDTO reopen(Long taskId) {
        Long companyId = requiredTenant();
        EmployeeChecklistTask task = findTask(taskId, companyId);
        Viewer viewer = viewer();
        requireCanComplete(task, viewer);
        if (ChecklistConstants.STATUS_CANCELLED.equals(task.getChecklist().getStatus())) {
            throw new BadRequestException("This checklist was cancelled.");
        }
        task.setStatus(ChecklistConstants.TASK_PENDING);
        task.setCompletedAt(null);
        task.setCompletedBy(null);
        taskRepository.save(task);
        refreshChecklistStatus(task.getChecklist());
        auditLogService.log("EmployeeChecklistTask", task.getId(), "REOPEN", "'" + task.getTitle() + "' reopened");
        return toTaskDTO(task, viewer);
    }

    @Transactional
    public OnboardingDtos.TaskDTO uploadDocument(Long taskId, MultipartFile file) {
        Long companyId = requiredTenant();
        EmployeeChecklistTask task = findTask(taskId, companyId);
        Viewer viewer = viewer();
        requireCanComplete(task, viewer);
        Employee employee = task.getChecklist().getEmployee();
        String type = task.getDocumentType() == null ? "OTHER" : task.getDocumentType();
        EmployeeDocumentS3StorageService.StoredFile stored = documentStorage.store(file, companyId, employee.getId(), type);
        EmployeeDocument doc = new EmployeeDocument();
        doc.setCompany(employee.getCompany());
        doc.setEmployee(employee);
        doc.setDocumentType(type);
        doc.setStatus(EmployeeDocument.STATUS_PENDING_REVIEW);
        doc.setS3ObjectKey(stored.key());
        doc.setOriginalFileName(stored.originalName());
        doc.setContentType(stored.contentType());
        doc.setFileSizeBytes(stored.sizeBytes());
        doc.setUploadedAt(LocalDateTime.now());
        doc.setNotes(trimTo("Uploaded for checklist task: " + task.getTitle(), 500));
        EmployeeDocument savedDoc = documentRepository.save(doc);
        task.setDocumentId(savedDoc.getId());
        taskRepository.save(task);
        auditLogService.log("EmployeeChecklistTask", task.getId(), "DOCUMENT_UPLOADED",
                "Document '" + stored.originalName() + "' uploaded for '" + task.getTitle() + "'");
        return toTaskDTO(task, viewer);
    }

    @Transactional
    public OnboardingDtos.ChecklistDTO cancel(Long checklistId) {
        Long companyId = requiredTenant();
        EmployeeChecklist checklist = checklistRepository.findByIdAndCompany_IdAndDeletedFalse(checklistId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist not found: " + checklistId));
        checklist.setStatus(ChecklistConstants.STATUS_CANCELLED);
        checklistRepository.save(checklist);
        auditLogService.log("EmployeeChecklist", checklist.getId(), "CANCEL",
                "Cancelled '" + checklist.getName() + "' for '" + checklist.getEmployee().getFullName() + "'");
        return toChecklistDTO(checklist, viewer());
    }

    private void refreshChecklistStatus(EmployeeChecklist checklist) {
        if (ChecklistConstants.STATUS_CANCELLED.equals(checklist.getStatus())) return;
        boolean allDone = checklist.getTasks().stream().filter(t -> !t.isDeleted())
                .noneMatch(t -> ChecklistConstants.TASK_PENDING.equals(t.getStatus()));
        if (allDone && !ChecklistConstants.STATUS_COMPLETED.equals(checklist.getStatus())) {
            checklist.setStatus(ChecklistConstants.STATUS_COMPLETED);
            checklist.setCompletedAt(LocalDateTime.now());
            checklistRepository.save(checklist);
        } else if (!allDone && ChecklistConstants.STATUS_COMPLETED.equals(checklist.getStatus())) {
            checklist.setStatus(ChecklistConstants.STATUS_IN_PROGRESS);
            checklist.setCompletedAt(null);
            checklistRepository.save(checklist);
        }
    }

    // ================================================================== reminders

    /**
     * Sends one reminder per owner per day listing their overdue tasks.
     * Idempotent per day via EmployeeChecklistTask.lastReminderOn and the
     * notification deduplication key (which includes the date).
     *
     * @return number of tasks reminded about
     */
    @Transactional
    public int sendOverdueReminders(LocalDate today) {
        List<EmployeeChecklistTask> overdue = taskRepository.findOverdue(today);
        Map<Long, Map<Long, List<EmployeeChecklistTask>>> byCompanyAndUser = new LinkedHashMap<>();
        Map<Long, List<EmployeeChecklistTask>> hrByChecklist = new LinkedHashMap<>();
        int reminded = 0;
        for (EmployeeChecklistTask task : overdue) {
            if (today.equals(task.getLastReminderOn())) continue;
            Long companyId = task.getCompany().getId();
            if (ChecklistConstants.OWNER_HR.equals(task.getOwnerType())) {
                hrByChecklist.computeIfAbsent(task.getChecklist().getId(), k -> new ArrayList<>()).add(task);
            } else {
                for (Long userId : recipientUserIds(task, companyId)) {
                    byCompanyAndUser.computeIfAbsent(companyId, k -> new LinkedHashMap<>())
                            .computeIfAbsent(userId, k -> new ArrayList<>()).add(task);
                }
            }
            task.setLastReminderOn(today);
            taskRepository.save(task);
            reminded++;
        }
        String reminderKey = "OnboardingReminder:" + today;
        byCompanyAndUser.forEach((companyId, byUser) -> byUser.forEach((userId, tasks) -> safeNotify(() ->
                notificationService.notifyUser(companyId, userId, "ONBOARDING_OVERDUE",
                        tasks.size() + " overdue checklist task" + (tasks.size() == 1 ? "" : "s"),
                        summary(tasks), reminderKey, userId, "HIGH"))));
        hrByChecklist.forEach((checklistId, tasks) -> {
            EmployeeChecklist checklist = tasks.get(0).getChecklist();
            safeNotify(() -> notificationService.notifyPermissionRecipients(checklist.getCompany().getId(),
                    checklist.getEmployee().getId(), MANAGE_PERMISSION, "ONBOARDING_OVERDUE",
                    "Overdue HR tasks for " + checklist.getEmployee().getFullName(), summary(tasks),
                    reminderKey, checklistId));
        });
        return reminded;
    }

    private static String summary(List<EmployeeChecklistTask> tasks) {
        StringBuilder sb = new StringBuilder();
        for (EmployeeChecklistTask t : tasks) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(t.getTitle()).append(" (").append(t.getChecklist().getEmployee().getFullName())
                    .append(", due ").append(t.getDueDate()).append(")");
            if (sb.length() > 900) {
                sb.append("...");
                break;
            }
        }
        return sb.toString();
    }

    private static void safeNotify(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            log.warn("Checklist reminder notification failed: {}", e.getMessage());
        }
    }

    // ================================================================== authorization

    /** Who is looking: HR flag, user and linked employee. */
    record Viewer(boolean hr, User user, Employee employee) {
        String displayName() {
            if (employee != null) return employee.getFullName();
            if (user != null) return user.getFullName() != null ? user.getFullName() : user.getUsername();
            return "system";
        }

        boolean hasRole(String role) {
            return user != null && user.getRoles().stream().anyMatch(r -> role.equals(r.getName()));
        }
    }

    Viewer viewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return new Viewer(false, null, null);
        Long tenant = TenantContext.getCurrentTenant();
        User user = userRepository.findByUsernameAndDeletedFalse(authentication.getName()).orElse(null);
        Employee employee = employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .filter(e -> e.getCompany() != null && (tenant == null || tenant.equals(e.getCompany().getId())))
                .orElse(null);
        boolean hr = authorizationService.hasOrganizationScope(MANAGE_PERMISSION);
        return new Viewer(hr, user, employee);
    }

    /** Ownership rules - see class javadoc. HR (ONBOARDING_MANAGE, org scope) may act on any task. */
    static boolean canComplete(EmployeeChecklistTask task, Viewer viewer) {
        if (viewer.hr()) return true;
        return isPersonalOwner(task, viewer);
    }

    static boolean isPersonalOwner(EmployeeChecklistTask task, Viewer viewer) {
        Long myEmployeeId = viewer.employee() != null ? viewer.employee().getId() : null;
        Long myUserId = viewer.user() != null ? viewer.user().getId() : null;
        switch (task.getOwnerType()) {
            case ChecklistConstants.OWNER_NEW_HIRE:
                return myEmployeeId != null && Objects.equals(myEmployeeId, task.getChecklist().getEmployee().getId());
            case ChecklistConstants.OWNER_MANAGER:
                if (myEmployeeId == null) return false;
                if (task.getOwnerEmployee() != null) return Objects.equals(myEmployeeId, task.getOwnerEmployee().getId());
                Employee manager = task.getChecklist().getEmployee().getReportingManager();
                return manager != null && Objects.equals(myEmployeeId, manager.getId());
            case ChecklistConstants.OWNER_IT:
                if (task.getOwnerUser() != null) return myUserId != null && Objects.equals(myUserId, task.getOwnerUser().getId());
                return viewer.hasRole(ChecklistConstants.IT_ROLE);
            case ChecklistConstants.OWNER_USER:
                return myUserId != null && task.getOwnerUser() != null && Objects.equals(myUserId, task.getOwnerUser().getId());
            default:
                return false;
        }
    }

    private static boolean isOwnChecklist(EmployeeChecklistTask task, Viewer viewer) {
        return viewer.employee() != null && Objects.equals(viewer.employee().getId(), task.getChecklist().getEmployee().getId());
    }

    private void requireCanComplete(EmployeeChecklistTask task, Viewer viewer) {
        if (!canComplete(task, viewer)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not an owner of this task.");
        }
    }

    /** Whether the viewer may see this checklist (HR, the employee, an owner of any of its tasks, or anyone with EMPLOYEE_VIEW over the employee). */
    @Transactional(readOnly = true)
    public boolean canViewChecklist(Long checklistId) {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null || checklistId == null) return false;
        EmployeeChecklist checklist = checklistRepository.findByIdAndCompany_IdAndDeletedFalse(checklistId, tenant).orElse(null);
        if (checklist == null) return false;
        Viewer viewer = viewer();
        if (viewer.hr()) return true;
        if (viewer.employee() != null && Objects.equals(viewer.employee().getId(), checklist.getEmployee().getId())) return true;
        return checklist.getTasks().stream().anyMatch(t -> isPersonalOwner(t, viewer))
                || authorizationService.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", checklist.getEmployee().getId());
    }

    // ================================================================== mapping

    OnboardingDtos.ChecklistDTO toChecklistDTO(EmployeeChecklist c, Viewer viewer) {
        LocalDate today = LocalDate.now(IST);
        List<EmployeeChecklistTask> tasks = c.getTasks().stream().filter(t -> !t.isDeleted())
                .sorted(Comparator.comparingInt(EmployeeChecklistTask::getSortOrder)
                        .thenComparing(EmployeeChecklistTask::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int total = tasks.size();
        int done = (int) tasks.stream().filter(t -> !ChecklistConstants.TASK_PENDING.equals(t.getStatus())).count();
        int overdue = (int) tasks.stream().filter(t -> isOverdue(t, today)).count();
        int percent = total == 0 ? 100 : (int) Math.round(done * 100.0 / total);
        Employee e = c.getEmployee();
        return new OnboardingDtos.ChecklistDTO(c.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(),
                c.getChecklistType(), c.getName(), c.getStatus(), c.getAnchorDate(), c.getStartedAt(), c.getCompletedAt(),
                total, done, overdue, percent, tasks.stream().map(t -> toTaskDTO(t, viewer)).toList());
    }

    OnboardingDtos.TaskDTO toTaskDTO(EmployeeChecklistTask t, Viewer viewer) {
        EmployeeChecklist c = t.getChecklist();
        boolean active = ChecklistConstants.STATUS_IN_PROGRESS.equals(c.getStatus())
                || ChecklistConstants.STATUS_COMPLETED.equals(c.getStatus());
        return new OnboardingDtos.TaskDTO(t.getId(), c.getId(), t.getTitle(), t.getDescription(), t.getOwnerType(),
                t.getOwnerUser() != null ? t.getOwnerUser().getId() : null, ownerName(t),
                t.getOwnerEmployee() != null ? t.getOwnerEmployee().getId() : null,
                t.getDueDate(), t.getStatus(), isOverdue(t, LocalDate.now(IST)), t.getCompletedAt(), t.getCompletedBy(),
                t.getRemarks(), t.isRequiresDocument(), t.getDocumentType(), t.getDocumentId(), t.getLinkKey(),
                active && viewer != null && canComplete(t, viewer),
                c.getEmployee().getId(), c.getEmployee().getFullName(), c.getChecklistType());
    }

    static boolean isOverdue(EmployeeChecklistTask t, LocalDate today) {
        return ChecklistConstants.TASK_PENDING.equals(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(today)
                && ChecklistConstants.STATUS_IN_PROGRESS.equals(t.getChecklist().getStatus());
    }

    private static String ownerName(EmployeeChecklistTask t) {
        return switch (t.getOwnerType()) {
            case ChecklistConstants.OWNER_NEW_HIRE -> t.getChecklist().getEmployee().getFullName();
            case ChecklistConstants.OWNER_MANAGER -> t.getOwnerEmployee() != null ? t.getOwnerEmployee().getFullName() : "Reporting manager";
            case ChecklistConstants.OWNER_IT -> t.getOwnerUser() != null ? displayName(t.getOwnerUser()) : "IT team";
            case ChecklistConstants.OWNER_USER -> t.getOwnerUser() != null ? displayName(t.getOwnerUser()) : "Unassigned";
            default -> "HR team";
        };
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    // ================================================================== helpers

    private ChecklistTemplate findTemplate(Long id, Long companyId) {
        return templateRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist template not found: " + id));
    }

    private EmployeeChecklistTask findTask(Long id, Long companyId) {
        return taskRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist task not found: " + id));
    }

    private static String trimTo(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
