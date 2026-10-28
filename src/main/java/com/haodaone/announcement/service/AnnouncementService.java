package com.haodaone.announcement.service;

import com.haodaone.announcement.dto.AnnouncementDtos.AcknowledgementReport;
import com.haodaone.announcement.dto.AnnouncementDtos.AcknowledgementRow;
import com.haodaone.announcement.dto.AnnouncementDtos.AdminItem;
import com.haodaone.announcement.dto.AnnouncementDtos.AnnouncementRequest;
import com.haodaone.announcement.dto.AnnouncementDtos.FeedItem;
import com.haodaone.announcement.dto.AnnouncementDtos.UnreadCount;
import com.haodaone.announcement.entity.Announcement;
import com.haodaone.announcement.entity.AnnouncementReceipt;
import com.haodaone.announcement.repository.AnnouncementReceiptRepository;
import com.haodaone.announcement.repository.AnnouncementRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.expense.service.AttachmentStorageService;
import com.haodaone.org.entity.Department;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> CATEGORIES = Set.of("GENERAL", "POLICY", "EVENT", "HOLIDAY", "URGENT");
    private static final int MAX_BODY = 20_000;
    private static final String ENTITY = "Announcement";

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptRepository receiptRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final AttachmentStorageService storageService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               AnnouncementReceiptRepository receiptRepository,
                               EmployeeRepository employeeRepository,
                               DepartmentRepository departmentRepository,
                               CompanyRepository companyRepository,
                               AuthorizationService authorizationService,
                               AuditLogService auditLogService,
                               AttachmentStorageService storageService) {
        this.announcementRepository = announcementRepository;
        this.receiptRepository = receiptRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.storageService = storageService;
    }

    // ------------------------------------------------------------------ employee feed

    @Transactional(readOnly = true)
    public List<FeedItem> feed() {
        Long companyId = requiredTenant();
        Employee me = authorizationService.resolveCurrentEmployee();
        Long departmentId = me == null || me.getDepartment() == null ? null : me.getDepartment().getId();
        LocalDateTime now = LocalDateTime.now(IST);
        Map<Long, AnnouncementReceipt> receipts = me == null ? Map.<Long, AnnouncementReceipt>of()
                : receiptRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalse(companyId, me.getId()).stream()
                .collect(Collectors.toMap(receipt -> receipt.getAnnouncement().getId(), Function.identity(), (a, b) -> a));
        return announcementRepository.findAllByCompany_IdAndStatusAndDeletedFalse(companyId, Announcement.STATUS_PUBLISHED).stream()
                .filter(announcement -> isLive(announcement, now))
                .filter(announcement -> AnnouncementAudience.includes(announcement, departmentId))
                .sorted(feedOrder())
                .map(announcement -> toFeedItem(announcement, receipts.get(announcement.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        List<FeedItem> items = feed();
        long unread = items.stream().filter(item -> !item.read()).count();
        long pending = items.stream().filter(item -> item.requiresAcknowledgement() && item.acknowledgedAt() == null).count();
        return new UnreadCount(unread, pending);
    }

    @Transactional
    public FeedItem markRead(Long id) {
        Employee me = requireEmployee();
        Announcement announcement = visibleTo(id, me);
        AnnouncementReceipt receipt = receiptFor(announcement, me);
        if (receipt.getReadAt() == null) {
            receipt.setReadAt(LocalDateTime.now(IST));
            receipt = receiptRepository.save(receipt);
        }
        return toFeedItem(announcement, receipt);
    }

    @Transactional
    public FeedItem acknowledge(Long id) {
        Employee me = requireEmployee();
        Announcement announcement = visibleTo(id, me);
        if (!announcement.isRequiresAcknowledgement()) {
            throw new BadRequestException("This announcement doesn't need an acknowledgement.");
        }
        AnnouncementReceipt receipt = receiptFor(announcement, me);
        LocalDateTime now = LocalDateTime.now(IST);
        if (receipt.getReadAt() == null) receipt.setReadAt(now);
        if (receipt.getAcknowledgedAt() == null) {
            receipt.setAcknowledgedAt(now);
            receipt = receiptRepository.save(receipt);
            auditLogService.log(ENTITY, announcement.getId(), "ACKNOWLEDGE",
                    me.getFullName() + " acknowledged '" + announcement.getTitle() + "'");
        }
        return toFeedItem(announcement, receipt);
    }

    public record AttachmentDownload(InputStreamResource resource, String fileName, String contentType) {
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(Long id) {
        Announcement announcement = load(id);
        if (!authorizationService.hasOrganizationScope("ANNOUNCEMENT_MANAGE")) {
            Employee me = requireEmployee();
            announcement = visibleTo(id, me);
        }
        if (announcement.getAttachmentS3Key() == null) throw new BadRequestException("This announcement has no attachment.");
        return new AttachmentDownload(storageService.retrieve(announcement.getAttachmentS3Key()),
                announcement.getAttachmentFileName() == null ? "attachment" : announcement.getAttachmentFileName(),
                announcement.getAttachmentContentType());
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public List<AdminItem> adminList() {
        Long companyId = requiredTenant();
        List<AnnouncementAudience.Member> members = members(companyId);
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : receiptRepository.countsByAnnouncement(companyId)) {
            counts.put(((Number) row[0]).longValue(), new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        Map<Long, String> departmentNames = departmentNames(companyId);
        LocalDateTime now = LocalDateTime.now(IST);
        return announcementRepository.findAllByCompany_IdAndDeletedFalseOrderByIdDesc(companyId).stream()
                .map(announcement -> toAdminItem(announcement, AnnouncementAudience.audienceOf(announcement, members).size(),
                        counts.getOrDefault(announcement.getId(), new long[]{0, 0}), departmentNames, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminItem adminGet(Long id) {
        Announcement announcement = load(id);
        return adminView(announcement);
    }

    @Transactional
    public AdminItem create(AnnouncementRequest request) {
        Long companyId = requiredTenant();
        Announcement announcement = new Announcement();
        announcement.setCompany(companyRepository.getReferenceById(companyId));
        announcement.setAuthorName(currentAuthorName());
        applyContent(announcement, request, companyId);
        applyPublishing(announcement, request);
        Announcement saved = announcementRepository.save(announcement);
        auditLogService.log(ENTITY, saved.getId(), "CREATE", "Created announcement '" + saved.getTitle() + "' (" + saved.getStatus() + ")");
        return adminView(saved);
    }

    @Transactional
    public AdminItem update(Long id, AnnouncementRequest request) {
        Announcement announcement = load(id);
        applyContent(announcement, request, announcement.getCompany().getId());
        if (!Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            applyPublishing(announcement, request);
        } else {
            validateExpiry(announcement.getExpiresAt(), announcement.getPublishedAt());
        }
        Announcement saved = announcementRepository.save(announcement);
        auditLogService.log(ENTITY, saved.getId(), "UPDATE", "Updated announcement '" + saved.getTitle() + "' (" + saved.getStatus() + ")");
        return adminView(saved);
    }

    @Transactional
    public AdminItem publishNow(Long id) {
        Announcement announcement = load(id);
        if (Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BadRequestException("This announcement is already published.");
        }
        LocalDateTime now = LocalDateTime.now(IST);
        if (announcement.getExpiresAt() != null && !announcement.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("The expiry time has already passed. Update it before publishing.");
        }
        announcement.setStatus(Announcement.STATUS_PUBLISHED);
        announcement.setPublishedAt(now);
        Announcement saved = announcementRepository.save(announcement);
        auditLogService.log(ENTITY, saved.getId(), "PUBLISH", "Published announcement '" + saved.getTitle() + "'");
        return adminView(saved);
    }

    /** Scheduler entry point: atomic SCHEDULED -> PUBLISHED; true when this call published it. */
    @Transactional
    public boolean publishScheduled(Long id, LocalDateTime now) {
        return announcementRepository.publishIfScheduled(id, now) == 1;
    }

    @Transactional
    public void delete(Long id) {
        Announcement announcement = load(id);
        announcement.setDeleted(true);
        announcement.setDeletedAt(LocalDateTime.now(IST));
        announcementRepository.save(announcement);
        auditLogService.log(ENTITY, announcement.getId(), "DELETE", "Deleted announcement '" + announcement.getTitle() + "'");
    }

    @Transactional
    public AdminItem uploadAttachment(Long id, MultipartFile file) {
        Announcement announcement = load(id);
        AttachmentStorageService.StoredAttachment stored = storageService.store(file,
                "announcements/" + announcement.getCompany().getId() + "/" + announcement.getId());
        String previous = announcement.getAttachmentS3Key();
        announcement.setAttachmentS3Key(stored.key());
        announcement.setAttachmentFileName(stored.originalName());
        announcement.setAttachmentContentType(stored.contentType());
        announcement.setAttachmentSizeBytes(stored.sizeBytes());
        Announcement saved = announcementRepository.save(announcement);
        if (previous != null) storageService.deleteQuietly(previous);
        return adminView(saved);
    }

    @Transactional
    public AdminItem removeAttachment(Long id) {
        Announcement announcement = load(id);
        String previous = announcement.getAttachmentS3Key();
        announcement.setAttachmentS3Key(null);
        announcement.setAttachmentFileName(null);
        announcement.setAttachmentContentType(null);
        announcement.setAttachmentSizeBytes(null);
        Announcement saved = announcementRepository.save(announcement);
        if (previous != null) storageService.deleteQuietly(previous);
        return adminView(saved);
    }

    @Transactional(readOnly = true)
    public AcknowledgementReport acknowledgements(Long id) {
        Announcement announcement = load(id);
        Long companyId = announcement.getCompany().getId();
        List<AnnouncementAudience.Member> audience = AnnouncementAudience.audienceOf(announcement, members(companyId));
        Set<Long> audienceIds = audience.stream().map(AnnouncementAudience.Member::employeeId).collect(Collectors.toSet());
        Map<Long, AnnouncementReceipt> receipts = receiptRepository.findAllByAnnouncement_IdAndDeletedFalse(announcement.getId()).stream()
                .collect(Collectors.toMap(receipt -> receipt.getEmployee().getId(), Function.identity(), (a, b) -> a));
        List<AcknowledgementRow> rows = new ArrayList<>();
        for (Employee employee : employeeRepository.findOrganizationStructureByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (!audienceIds.contains(employee.getId())) continue;
            AnnouncementReceipt receipt = receipts.get(employee.getId());
            rows.add(new AcknowledgementRow(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    receipt == null ? null : ist(receipt.getReadAt()),
                    receipt == null ? null : ist(receipt.getAcknowledgedAt())));
        }
        long read = rows.stream().filter(row -> row.readAt() != null).count();
        long acknowledged = rows.stream().filter(row -> row.acknowledgedAt() != null).count();
        long pending = announcement.isRequiresAcknowledgement() ? rows.size() - acknowledged : 0;
        return new AcknowledgementReport(announcement.getId(), announcement.getTitle(), announcement.isRequiresAcknowledgement(),
                announcement.getAcknowledgementDueDate(), rows.size(), read, acknowledged, pending, rows);
    }

    @Transactional(readOnly = true)
    public String acknowledgementsCsv(Long id) {
        AcknowledgementReport report = acknowledgements(id);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder csv = new StringBuilder("Employee Code,Employee,Department,Read At,Acknowledged At,Status\n");
        for (AcknowledgementRow row : report.rows()) {
            String status = row.acknowledgedAt() != null ? "Acknowledged"
                    : report.requiresAcknowledgement() ? "Pending" : row.readAt() != null ? "Read" : "Unread";
            csv.append(String.join(",",
                    cell(row.employeeCode()), cell(row.employeeName()), cell(row.departmentName()),
                    cell(row.readAt() == null ? "" : row.readAt().format(format)),
                    cell(row.acknowledgedAt() == null ? "" : row.acknowledgedAt().format(format)),
                    cell(status))).append('\n');
        }
        return csv.toString();
    }

    public record ReminderBatch(Long companyId, Long announcementId, String title, LocalDate dueDate, List<Long> userIds) {
    }

    /** Logins in the audience that still haven't acknowledged; the controller fans out via AnnouncementNotifier. */
    @Transactional(readOnly = true)
    public ReminderBatch pendingReminderRecipients(Long id) {
        Announcement announcement = load(id);
        if (!announcement.isRequiresAcknowledgement()) {
            throw new BadRequestException("This announcement doesn't require acknowledgement.");
        }
        if (!Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BadRequestException("Reminders can only be sent for published announcements.");
        }
        Long companyId = announcement.getCompany().getId();
        Set<Long> acknowledged = receiptRepository.findAllByAnnouncement_IdAndDeletedFalse(announcement.getId()).stream()
                .filter(receipt -> receipt.getAcknowledgedAt() != null)
                .map(receipt -> receipt.getEmployee().getId())
                .collect(Collectors.toSet());
        List<Long> userIds = AnnouncementAudience.audienceOf(announcement, members(companyId)).stream()
                .filter(member -> !acknowledged.contains(member.employeeId()))
                .map(AnnouncementAudience.Member::userId)
                .toList();
        auditLogService.log(ENTITY, announcement.getId(), "REMIND", "Sent acknowledgement reminders to " + userIds.size() + " employee(s)");
        return new ReminderBatch(companyId, announcement.getId(), announcement.getTitle(),
                announcement.getAcknowledgementDueDate(), userIds);
    }

    // ------------------------------------------------------------------ internals

    private void applyContent(Announcement announcement, AnnouncementRequest request, Long companyId) {
        if (request == null) throw new BadRequestException("Announcement details are required.");
        String title = AnnouncementAudience.sanitize(request.title());
        if (title == null || title.isEmpty()) throw new BadRequestException("Title is required.");
        if (title.length() > 200) throw new BadRequestException("Title must be 200 characters or fewer.");
        String body = AnnouncementAudience.sanitize(request.body());
        if (body == null || body.isEmpty()) throw new BadRequestException("Message is required.");
        if (body.length() > MAX_BODY) throw new BadRequestException("Message must be " + MAX_BODY + " characters or fewer.");
        String category = request.category() == null || request.category().isBlank() ? "GENERAL"
                : request.category().trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) throw new BadRequestException("Category must be one of " + CATEGORIES + ".");

        String audienceType = request.audienceType() == null || request.audienceType().isBlank() ? Announcement.AUDIENCE_ALL
                : request.audienceType().trim().toUpperCase(Locale.ROOT);
        String departmentIds = null;
        if (Announcement.AUDIENCE_DEPARTMENTS.equals(audienceType)) {
            Set<Long> ids = new LinkedHashSet<>(request.departmentIds() == null ? List.of() : request.departmentIds());
            ids.remove(null);
            if (ids.isEmpty()) throw new BadRequestException("Pick at least one department for the audience.");
            for (Long departmentId : ids) {
                departmentRepository.findByIdAndCompany_IdAndDeletedFalse(departmentId, companyId)
                        .orElseThrow(() -> new BadRequestException("Unknown department in the audience."));
            }
            departmentIds = AnnouncementAudience.joinIds(ids);
            if (departmentIds.length() > 1000) throw new BadRequestException("Too many departments selected.");
        } else if (!Announcement.AUDIENCE_ALL.equals(audienceType)) {
            throw new BadRequestException("Audience must be ALL or DEPARTMENTS.");
        }

        boolean requiresAck = Boolean.TRUE.equals(request.requiresAcknowledgement());
        LocalDate dueDate = requiresAck ? request.acknowledgementDueDate() : null;
        if (dueDate != null && dueDate.isBefore(LocalDate.now(IST))
                && (announcement.getAcknowledgementDueDate() == null || !dueDate.equals(announcement.getAcknowledgementDueDate()))) {
            throw new BadRequestException("Acknowledgement due date can't be in the past.");
        }

        announcement.setTitle(title);
        announcement.setBody(body);
        announcement.setCategory(category);
        announcement.setAudienceType(audienceType);
        announcement.setAudienceDepartmentIds(departmentIds);
        announcement.setPinned(Boolean.TRUE.equals(request.pinned()));
        announcement.setRequiresAcknowledgement(requiresAck);
        announcement.setAcknowledgementDueDate(dueDate);
        announcement.setExpiresAt(request.expiresAt());
    }

    private void applyPublishing(Announcement announcement, AnnouncementRequest request) {
        String mode = request.publishMode() == null || request.publishMode().isBlank() ? "DRAFT"
                : request.publishMode().trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now(IST);
        switch (mode) {
            case "DRAFT" -> {
                announcement.setStatus(Announcement.STATUS_DRAFT);
                announcement.setPublishAt(request.publishAt());
                validateExpiry(announcement.getExpiresAt(), request.publishAt() != null ? request.publishAt() : now);
            }
            case "NOW" -> {
                validateExpiry(announcement.getExpiresAt(), now);
                announcement.setStatus(Announcement.STATUS_PUBLISHED);
                announcement.setPublishAt(now);
                announcement.setPublishedAt(now);
            }
            case "SCHEDULE" -> {
                if (request.publishAt() == null) throw new BadRequestException("Pick a date and time to publish.");
                if (!request.publishAt().isAfter(now)) throw new BadRequestException("Scheduled time must be in the future.");
                validateExpiry(announcement.getExpiresAt(), request.publishAt());
                announcement.setStatus(Announcement.STATUS_SCHEDULED);
                announcement.setPublishAt(request.publishAt());
            }
            default -> throw new BadRequestException("Publish mode must be DRAFT, NOW or SCHEDULE.");
        }
    }

    private static void validateExpiry(LocalDateTime expiresAt, LocalDateTime publishTime) {
        if (expiresAt == null) return;
        LocalDateTime reference = publishTime == null ? LocalDateTime.now(IST) : publishTime;
        if (!expiresAt.isAfter(reference)) throw new BadRequestException("Expiry must be after the publish time.");
    }

    private AnnouncementReceipt receiptFor(Announcement announcement, Employee me) {
        return receiptRepository.findByAnnouncement_IdAndEmployee_IdAndDeletedFalse(announcement.getId(), me.getId())
                .orElseGet(() -> {
                    AnnouncementReceipt created = new AnnouncementReceipt();
                    created.setCompany(announcement.getCompany());
                    created.setAnnouncement(announcement);
                    created.setEmployee(me);
                    return created;
                });
    }

    private Announcement visibleTo(Long id, Employee me) {
        Announcement announcement = load(id);
        Long departmentId = me.getDepartment() == null ? null : me.getDepartment().getId();
        if (!Announcement.STATUS_PUBLISHED.equals(announcement.getStatus())
                || !isLive(announcement, LocalDateTime.now(IST))
                || !AnnouncementAudience.includes(announcement, departmentId)) {
            throw new ResourceNotFoundException("Announcement not found.");
        }
        return announcement;
    }

    private static boolean isLive(Announcement announcement, LocalDateTime now) {
        return announcement.getExpiresAt() == null || announcement.getExpiresAt().isAfter(now);
    }

    static Comparator<Announcement> feedOrder() {
        return Comparator.comparing(Announcement::isPinned).reversed()
                .thenComparing(Announcement::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Announcement::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Announcement load(Long id) {
        return announcementRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found."));
    }

    private List<AnnouncementAudience.Member> members(Long companyId) {
        return AnnouncementAudience.toMembers(
                announcementRepository.findAudienceMembers(companyId, AnnouncementAudience.EXCLUDED_STATUSES));
    }

    private Map<Long, String> departmentNames(Long companyId) {
        return departmentRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private AdminItem adminView(Announcement announcement) {
        Long companyId = announcement.getCompany().getId();
        long audience = AnnouncementAudience.audienceOf(announcement, members(companyId)).size();
        List<AnnouncementReceipt> receipts = announcement.getId() == null ? List.of()
                : receiptRepository.findAllByAnnouncement_IdAndDeletedFalse(announcement.getId());
        long read = receipts.stream().filter(receipt -> receipt.getReadAt() != null).count();
        long acknowledged = receipts.stream().filter(receipt -> receipt.getAcknowledgedAt() != null).count();
        return toAdminItem(announcement, audience, new long[]{read, acknowledged}, departmentNames(companyId), LocalDateTime.now(IST));
    }

    private static AdminItem toAdminItem(Announcement announcement, long audienceCount, long[] counts,
                                         Map<Long, String> departmentNames, LocalDateTime now) {
        List<Long> departmentIds = new ArrayList<>(AnnouncementAudience.parseIds(announcement.getAudienceDepartmentIds()));
        List<String> names = departmentIds.stream().map(id -> departmentNames.getOrDefault(id, "Department #" + id)).toList();
        return new AdminItem(announcement.getId(), announcement.getTitle(), announcement.getBody(), announcement.getCategory(),
                announcement.getAudienceType(), departmentIds, names, announcement.getStatus(), !isLive(announcement, now),
                ist(announcement.getPublishAt()), ist(announcement.getPublishedAt()), ist(announcement.getExpiresAt()),
                announcement.isPinned(), announcement.isRequiresAcknowledgement(), announcement.getAcknowledgementDueDate(),
                announcement.getAttachmentS3Key() != null, announcement.getAttachmentFileName(), announcement.getAuthorName(),
                ist(announcement.getCreatedAt()), audienceCount, counts[0], counts[1]);
    }

    private static FeedItem toFeedItem(Announcement announcement, AnnouncementReceipt receipt) {
        return new FeedItem(announcement.getId(), announcement.getTitle(), announcement.getBody(), announcement.getCategory(),
                announcement.isPinned(), ist(announcement.getPublishedAt()), ist(announcement.getExpiresAt()),
                announcement.getAuthorName(), announcement.isRequiresAcknowledgement(), announcement.getAcknowledgementDueDate(),
                announcement.getAttachmentS3Key() != null, announcement.getAttachmentFileName(),
                receipt != null && receipt.getReadAt() != null,
                receipt == null ? null : ist(receipt.getAcknowledgedAt()));
    }

    private Employee requireEmployee() {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me == null) throw new BadRequestException("Your login is not linked to an active employee profile.");
        return me;
    }

    private String currentAuthorName() {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me != null) return truncate(me.getFullName(), 150);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : truncate(authentication.getName(), 150);
    }

    static OffsetDateTime ist(LocalDateTime value) {
        return value == null ? null : value.atZone(IST).toOffsetDateTime();
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("A company context is required.");
        return companyId;
    }
}
