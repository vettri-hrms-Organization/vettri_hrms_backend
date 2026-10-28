package com.haodaone.letters.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.letters.dto.LetterDtos;
import com.haodaone.letters.entity.IssuedLetter;
import com.haodaone.letters.entity.LetterTemplate;
import com.haodaone.letters.entity.LetterType;
import com.haodaone.letters.entity.LetterheadSettings;
import com.haodaone.letters.repository.IssuedLetterRepository;
import com.haodaone.letters.repository.LetterTemplateRepository;
import com.haodaone.letters.repository.LetterheadSettingsRepository;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.service.CandidateService;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HR letters: per-company template library (built-ins seeded on first use),
 * letterhead settings, generation to PDF (stored in S3 + mirrored as an
 * EmployeeDocument of type LETTER), optional email, and generated offer
 * letters for recruitment candidates.
 */
@Service
public class LetterService {

    private static final Logger log = LoggerFactory.getLogger(LetterService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final long MAX_LOGO_BYTES = 1024L * 1024L;
    public static final String DOCUMENT_TYPE_LETTER = "LETTER";

    private final LetterTemplateRepository templateRepository;
    private final LetterheadSettingsRepository letterheadRepository;
    private final IssuedLetterRepository issuedLetterRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final SalaryStructureRepository salaryStructureRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final LetterPdfService pdfService;
    private final LetterStorageService storage;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public LetterService(LetterTemplateRepository templateRepository, LetterheadSettingsRepository letterheadRepository,
                         IssuedLetterRepository issuedLetterRepository, CompanyRepository companyRepository,
                         EmployeeRepository employeeRepository, CandidateRepository candidateRepository,
                         CandidateService candidateService, SalaryStructureRepository salaryStructureRepository,
                         EmployeeDocumentRepository employeeDocumentRepository, LetterPdfService pdfService,
                         LetterStorageService storage, EmailService emailService, AuditLogService auditLogService) {
        this.templateRepository = templateRepository;
        this.letterheadRepository = letterheadRepository;
        this.issuedLetterRepository = issuedLetterRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
        this.salaryStructureRepository = salaryStructureRepository;
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.pdfService = pdfService;
        this.storage = storage;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ templates

    @Transactional
    public List<LetterDtos.TemplateDTO> listTemplates() {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        return templateRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(LetterDtos.TemplateDTO::from).toList();
    }

    /** Seeds the built-in templates the first time a company uses letters. Idempotent. */
    @Transactional
    public void ensureDefaults(Long companyId) {
        if (templateRepository.existsByCompany_Id(companyId)) return;
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));
        for (DefaultLetterTemplates.Definition d : DefaultLetterTemplates.ALL) {
            LetterTemplate t = new LetterTemplate();
            t.setCompany(company);
            t.setLetterType(d.letterType());
            t.setName(d.name());
            t.setTitle(d.title());
            t.setBody(d.body());
            t.setBuiltIn(true);
            t.setActive(true);
            templateRepository.save(t);
        }
    }

    @Transactional(readOnly = true)
    public LetterDtos.TemplateDTO getTemplate(Long id) {
        return LetterDtos.TemplateDTO.from(findTemplate(id));
    }

    @Transactional
    public LetterDtos.TemplateDTO createTemplate(LetterDtos.TemplateRequest request) {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        LetterTemplate t = new LetterTemplate();
        t.setCompany(companyRepository.getReferenceById(companyId));
        t.setBuiltIn(false);
        applyTemplate(t, request);
        LetterTemplate saved = templateRepository.save(t);
        auditLogService.log("LetterTemplate", saved.getId(), "CREATE", "Letter template '" + saved.getName() + "' created");
        return LetterDtos.TemplateDTO.from(saved);
    }

    @Transactional
    public LetterDtos.TemplateDTO updateTemplate(Long id, LetterDtos.TemplateRequest request) {
        LetterTemplate t = findTemplate(id);
        applyTemplate(t, request);
        LetterTemplate saved = templateRepository.save(t);
        auditLogService.log("LetterTemplate", saved.getId(), "UPDATE", "Letter template '" + saved.getName() + "' updated");
        return LetterDtos.TemplateDTO.from(saved);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        LetterTemplate t = findTemplate(id);
        if (t.isBuiltIn()) {
            throw new BadRequestException("Built-in templates can't be deleted - deactivate or edit it instead.");
        }
        t.setDeleted(true);
        t.setDeletedAt(LocalDateTime.now());
        templateRepository.save(t);
        auditLogService.log("LetterTemplate", id, "DELETE", "Letter template '" + t.getName() + "' deleted");
    }

    private void applyTemplate(LetterTemplate t, LetterDtos.TemplateRequest request) {
        if (request == null) throw new BadRequestException("Template details are required.");
        String type = request.letterType() == null || request.letterType().isBlank() ? LetterType.CUSTOM
                : request.letterType().trim().toUpperCase(Locale.ROOT);
        if (!LetterType.ALL.contains(type)) {
            throw new BadRequestException("Unknown letter type: " + request.letterType() + ". Must be one of " + LetterType.ALL);
        }
        if (request.name() == null || request.name().isBlank()) throw new BadRequestException("Template name is required.");
        if (request.title() == null || request.title().isBlank()) throw new BadRequestException("Letter title is required.");
        if (request.name().length() > 150 || request.title().length() > 200) {
            throw new BadRequestException("Template name (150) or title (200) is too long.");
        }
        LetterRenderer.validateTemplate(request.body());
        LetterRenderer.validateTemplate(request.title() + " "); // title may use placeholders too
        t.setLetterType(type);
        t.setName(request.name().trim());
        t.setTitle(request.title().trim());
        t.setBody(request.body());
        if (request.active() != null) t.setActive(request.active());
    }

    public List<LetterDtos.PlaceholderDTO> placeholders() {
        List<LetterDtos.PlaceholderDTO> list = new ArrayList<>();
        for (String key : LetterRenderer.KNOWN_PLACEHOLDERS) {
            int dot = key.indexOf('.');
            list.add(new LetterDtos.PlaceholderDTO(key, dot < 0 ? "general" : key.substring(0, dot)));
        }
        return list;
    }

    // ------------------------------------------------------------------ letterhead

    @Transactional(readOnly = true)
    public LetterDtos.LetterheadDTO getLetterhead() {
        Long companyId = requiredTenant();
        Company company = companyRepository.findById(companyId).orElse(null);
        return LetterDtos.LetterheadDTO.from(letterheadRepository.findFirstByCompany_IdAndDeletedFalseOrderByIdAsc(companyId).orElse(null),
                companyName(company));
    }

    @Transactional
    public LetterDtos.LetterheadDTO updateLetterhead(LetterDtos.LetterheadRequest request) {
        Long companyId = requiredTenant();
        LetterheadSettings s = letterheadFor(companyId);
        s.setAddress(trimTo(request.address(), 1000));
        s.setSignatoryName(trimTo(request.signatoryName(), 150));
        s.setSignatoryDesignation(trimTo(request.signatoryDesignation(), 150));
        s.setFooter(trimTo(request.footer(), 500));
        LetterheadSettings saved = letterheadRepository.save(s);
        auditLogService.log("LetterheadSettings", saved.getId(), "UPDATE", "Letterhead settings updated");
        return LetterDtos.LetterheadDTO.from(saved, companyName(saved.getCompany()));
    }

    @Transactional
    public LetterDtos.LetterheadDTO uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Please choose a logo image.");
        if (file.getSize() > MAX_LOGO_BYTES) throw new BadRequestException("Logo must be 1 MB or smaller.");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            default -> throw new BadRequestException("Logo must be a PNG or JPG image.");
        };
        Long companyId = requiredTenant();
        LetterheadSettings s = letterheadFor(companyId);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded logo.");
        }
        String key = storage.logoKey(companyId, ext);
        storage.put(key, bytes, contentType);
        s.setLogoS3Key(key);
        s.setLogoContentType(contentType);
        LetterheadSettings saved = letterheadRepository.save(s);
        auditLogService.log("LetterheadSettings", saved.getId(), "LOGO_UPLOADED", "Letterhead logo uploaded");
        return LetterDtos.LetterheadDTO.from(saved, companyName(saved.getCompany()));
    }

    @Transactional
    public LetterDtos.LetterheadDTO removeLogo() {
        Long companyId = requiredTenant();
        LetterheadSettings s = letterheadFor(companyId);
        s.setLogoS3Key(null);
        s.setLogoContentType(null);
        LetterheadSettings saved = letterheadRepository.save(s);
        return LetterDtos.LetterheadDTO.from(saved, companyName(saved.getCompany()));
    }

    /** [bytes, contentType] of the current logo, or null. */
    @Transactional(readOnly = true)
    public Object[] logo() {
        Long companyId = requiredTenant();
        LetterheadSettings s = letterheadRepository.findFirstByCompany_IdAndDeletedFalseOrderByIdAsc(companyId).orElse(null);
        if (s == null || s.getLogoS3Key() == null) return null;
        return new Object[]{storage.get(s.getLogoS3Key()), s.getLogoContentType()};
    }

    private LetterheadSettings letterheadFor(Long companyId) {
        return letterheadRepository.findFirstByCompany_IdAndDeletedFalseOrderByIdAsc(companyId).orElseGet(() -> {
            LetterheadSettings created = new LetterheadSettings();
            created.setCompany(companyRepository.findById(companyId)
                    .orElseThrow(() -> new BadRequestException("Company not found: " + companyId)));
            return created;
        });
    }

    // ------------------------------------------------------------------ generation

    @Transactional
    public LetterDtos.PreviewResponse preview(LetterDtos.GenerateRequest request) {
        Long companyId = requiredTenant();
        Rendered rendered = render(companyId, request);
        return new LetterDtos.PreviewResponse(rendered.title(), rendered.body());
    }

    /** Generates a letter for an employee: PDF to S3, IssuedLetter + EmployeeDocument, optional email. */
    @Transactional
    public LetterDtos.IssuedLetterDTO generateForEmployee(LetterDtos.GenerateRequest request) {
        Long companyId = requiredTenant();
        if (request == null || request.employeeId() == null) throw new BadRequestException("Choose an employee for this letter.");
        if (request.candidateId() != null) throw new BadRequestException("Use the candidate offer-letter action for candidates.");
        Rendered rendered = render(companyId, request);
        Employee employee = rendered.employee();
        byte[] pdf = pdfService.render(rendered.letterhead(), rendered.title(), rendered.body());
        String fileName = fileName(rendered.title(), employee.getEmployeeCode());
        String key = storage.employeeLetterKey(companyId, employee.getId());
        storage.put(key, pdf, "application/pdf");

        EmployeeDocument doc = new EmployeeDocument();
        doc.setCompany(employee.getCompany());
        doc.setEmployee(employee);
        doc.setDocumentType(DOCUMENT_TYPE_LETTER);
        doc.setStatus(EmployeeDocument.STATUS_APPROVED);
        doc.setS3ObjectKey(key);
        doc.setOriginalFileName(fileName);
        doc.setContentType("application/pdf");
        doc.setFileSizeBytes((long) pdf.length);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setIssueDate(LocalDate.now(IST));
        doc.setNotes(trimTo(rendered.title(), 500));
        EmployeeDocument savedDoc = employeeDocumentRepository.save(doc);

        IssuedLetter letter = new IssuedLetter();
        letter.setCompany(employee.getCompany());
        letter.setTemplate(rendered.template());
        letter.setEmployee(employee);
        letter.setLetterType(rendered.template().getLetterType());
        letter.setTitle(trimTo(rendered.title(), 200));
        letter.setRenderedBody(rendered.body());
        letter.setS3ObjectKey(key);
        letter.setFileName(fileName);
        letter.setFileSizeBytes((long) pdf.length);
        letter.setEmployeeDocumentId(savedDoc.getId());
        letter.setIssuedAt(LocalDateTime.now());
        letter.setIssuedBy(currentActorName());
        IssuedLetter saved = issuedLetterRepository.save(letter);
        auditLogService.log("IssuedLetter", saved.getId(), "GENERATE",
                "'" + saved.getTitle() + "' issued to '" + employee.getFullName() + "' (" + employee.getEmployeeCode() + ")");

        if (Boolean.TRUE.equals(request.sendEmail())) {
            emailLetter(saved, pdf);
        }
        return LetterDtos.IssuedLetterDTO.from(saved);
    }

    /**
     * Generates an offer letter for a recruitment candidate from an OFFER
     * template and attaches it as the candidate's current offer letter, so
     * the existing "Send Offer Letter" action sends this PDF.
     */
    @Transactional
    public LetterDtos.IssuedLetterDTO generateOfferLetter(Long candidateId, LetterDtos.GenerateRequest request) {
        Long companyId = requiredTenant();
        LetterDtos.GenerateRequest effective = new LetterDtos.GenerateRequest(
                request == null ? null : request.templateId(), null, candidateId,
                request == null ? null : request.customFields(), request == null ? null : request.title(), false);
        Rendered rendered = render(companyId, effective);
        Candidate candidate = rendered.candidate();
        byte[] pdf = pdfService.render(rendered.letterhead(), rendered.title(), rendered.body());
        String fileName = fileName(rendered.title(), candidate.getFullName());
        String key = storage.candidateLetterKey(companyId, candidateId);
        storage.put(key, pdf, "application/pdf");

        IssuedLetter letter = new IssuedLetter();
        letter.setCompany(companyRepository.getReferenceById(companyId));
        letter.setTemplate(rendered.template());
        letter.setCandidateId(candidateId);
        letter.setLetterType(LetterType.OFFER);
        letter.setTitle(trimTo(rendered.title(), 200));
        letter.setRenderedBody(rendered.body());
        letter.setS3ObjectKey(key);
        letter.setFileName(fileName);
        letter.setFileSizeBytes((long) pdf.length);
        letter.setIssuedAt(LocalDateTime.now());
        letter.setIssuedBy(currentActorName());
        IssuedLetter saved = issuedLetterRepository.save(letter);

        candidateService.attachGeneratedOfferLetter(candidateId, pdf, fileName);
        auditLogService.log("IssuedLetter", saved.getId(), "GENERATE",
                "Offer letter generated for candidate '" + candidate.getFullName() + "'");
        return LetterDtos.IssuedLetterDTO.from(saved);
    }

    @Transactional
    public LetterDtos.IssuedLetterDTO email(Long id) {
        IssuedLetter letter = findLetter(id);
        if (letter.getEmployee() == null) {
            throw new BadRequestException("Candidate offer letters are sent from the candidate's offer letter panel.");
        }
        emailLetter(letter, storage.get(letter.getS3ObjectKey()));
        return LetterDtos.IssuedLetterDTO.from(letter);
    }

    private void emailLetter(IssuedLetter letter, byte[] pdf) {
        Employee employee = letter.getEmployee();
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new BadRequestException("This employee has no email address on file.");
        }
        String company = companyName(letter.getCompany());
        String body = "<p>Dear " + HtmlUtils.htmlEscape(employee.getFirstName() == null ? employee.getFullName() : employee.getFirstName()) + ",</p>"
                + "<p>Please find attached your <strong>" + HtmlUtils.htmlEscape(letter.getTitle()) + "</strong>"
                + (company.isBlank() ? "" : " from " + HtmlUtils.htmlEscape(company)) + ".</p>"
                + "<p>You can also download it any time from your documents in Vettri HRMS.</p>";
        boolean delivered = emailService.sendLetterEmail(employee.getEmail(), employee.getFullName(),
                letter.getTitle() + (company.isBlank() ? "" : " - " + company), body, pdf, letter.getFileName());
        letter.setEmailedAt(LocalDateTime.now());
        letter.setEmailStatus(delivered ? IssuedLetter.EMAIL_SENT : IssuedLetter.EMAIL_FAILED);
        issuedLetterRepository.save(letter);
        auditLogService.log("IssuedLetter", letter.getId(), "EMAIL",
                "'" + letter.getTitle() + "' emailed to " + employee.getEmail() + " (" + letter.getEmailStatus() + ")");
    }

    // ------------------------------------------------------------------ issued letters

    @Transactional(readOnly = true)
    public List<LetterDtos.IssuedLetterDTO> listForEmployee(Long employeeId) {
        return issuedLetterRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIssuedAtDesc(requiredTenant(), employeeId)
                .stream().map(LetterDtos.IssuedLetterDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LetterDtos.IssuedLetterDTO> listForCandidate(Long candidateId) {
        return issuedLetterRepository.findAllByCompany_IdAndCandidateIdAndDeletedFalseOrderByIssuedAtDesc(requiredTenant(), candidateId)
                .stream().map(LetterDtos.IssuedLetterDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LetterDtos.IssuedLetterDTO> listRecent() {
        return issuedLetterRepository.findTop100ByCompany_IdAndDeletedFalseOrderByIssuedAtDesc(requiredTenant())
                .stream().map(LetterDtos.IssuedLetterDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LetterDtos.IssuedLetterDTO> listMine() {
        Employee me = currentEmployee();
        if (me == null) return List.of();
        return listForEmployee(me.getId());
    }

    public record Download(byte[] bytes, String fileName) {}

    @Transactional(readOnly = true)
    public Download download(Long id) {
        IssuedLetter letter = findLetter(id);
        return new Download(storage.get(letter.getS3ObjectKey()), letter.getFileName() == null ? "letter.pdf" : letter.getFileName());
    }

    /** For @PreAuthorize: the employee a letter was issued to (null for candidate letters / unknown ids). */
    @Transactional(readOnly = true)
    public Long resolveEmployeeIdForLetter(Long id) {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null || id == null) return null;
        return issuedLetterRepository.findByIdAndCompany_IdAndDeletedFalse(id, tenant)
                .map(l -> l.getEmployee() != null ? l.getEmployee().getId() : null)
                .orElse(null);
    }

    // ------------------------------------------------------------------ rendering

    private record Rendered(LetterTemplate template, String title, String body, LetterPdfService.Letterhead letterhead,
                            Employee employee, Candidate candidate) {}

    private Rendered render(Long companyId, LetterDtos.GenerateRequest request) {
        if (request == null) throw new BadRequestException("Letter details are required.");
        ensureDefaults(companyId);
        boolean forCandidate = request.candidateId() != null;
        if (!forCandidate && request.employeeId() == null) throw new BadRequestException("Choose an employee or candidate.");

        LetterTemplate template;
        if (request.templateId() != null) {
            template = findTemplate(request.templateId());
        } else if (forCandidate) {
            template = templateRepository.findFirstByCompany_IdAndLetterTypeAndActiveTrueAndDeletedFalseOrderByIdAsc(companyId, LetterType.OFFER)
                    .orElseThrow(() -> new BadRequestException("No active offer letter template - create one under Settings > Letter templates."));
        } else {
            throw new BadRequestException("Choose a letter template.");
        }
        if (!template.isActive()) throw new BadRequestException("Template '" + template.getName() + "' is inactive.");

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));
        LetterheadSettings settings = letterheadRepository.findFirstByCompany_IdAndDeletedFalseOrderByIdAsc(companyId).orElse(null);

        Map<String, String> values = new HashMap<>();
        String companyAddress = settings != null && settings.getAddress() != null && !settings.getAddress().isBlank()
                ? settings.getAddress() : company.getBillingAddress();
        values.put("company.name", companyName(company));
        values.put("company.address", companyAddress);
        values.put("today", DATE.format(LocalDate.now(IST)));
        values.put("signatory.name", settings != null ? settings.getSignatoryName() : null);
        values.put("signatory.designation", settings != null ? settings.getSignatoryDesignation() : null);

        Employee employee = null;
        Candidate candidate = null;
        if (forCandidate) {
            candidate = candidateRepository.findByIdAndJobOpening_Company_IdAndDeletedFalse(request.candidateId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + request.candidateId()));
            putCandidateValues(values, candidate);
        } else {
            employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.employeeId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.employeeId()));
            putEmployeeValues(values, employee, companyId);
        }

        Map<String, String> custom = request.customFields() == null ? Map.of() : request.customFields();
        String titleSource = request.title() != null && !request.title().isBlank() ? request.title() : template.getTitle();
        String title = LetterRenderer.render(titleSource + " ", values, custom).trim();
        String body = LetterRenderer.render(template.getBody(), values, custom);

        byte[] logo = null;
        if (settings != null && settings.getLogoS3Key() != null) {
            try {
                logo = storage.get(settings.getLogoS3Key());
            } catch (RuntimeException e) {
                log.warn("Letterhead logo unavailable for company {}: {}", companyId, e.getMessage());
            }
        }
        LetterPdfService.Letterhead letterhead = new LetterPdfService.Letterhead(
                LetterRenderer.sanitize(companyName(company)), LetterRenderer.sanitize(companyAddress), logo,
                settings != null ? LetterRenderer.sanitize(settings.getSignatoryName()) : null,
                settings != null ? LetterRenderer.sanitize(settings.getSignatoryDesignation()) : null,
                settings != null ? LetterRenderer.sanitize(settings.getFooter()) : null);
        return new Rendered(template, title, body, letterhead, employee, candidate);
    }

    void putEmployeeValues(Map<String, String> values, Employee e, Long companyId) {
        values.put("employee.fullName", e.getFullName());
        values.put("employee.firstName", e.getFirstName());
        values.put("employee.code", e.getEmployeeCode());
        values.put("employee.email", e.getEmail());
        values.put("employee.designation", e.getDesignation() != null ? e.getDesignation().getTitle() : null);
        values.put("employee.department", e.getDepartment() != null ? e.getDepartment().getName() : null);
        values.put("employee.dateOfJoining", e.getDateOfJoining() != null ? DATE.format(e.getDateOfJoining()) : null);
        values.put("employee.lastWorkingDate", e.getLastWorkingDate() != null ? DATE.format(e.getLastWorkingDate()) : null);
        values.put("employee.address", e.getAddress());
        SalaryStructure structure = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, e.getId()).orElse(null);
        if (structure != null && structure.getGrossSalary() != null) {
            values.put("salary.monthlyGross", money(structure.getGrossSalary()));
            values.put("salary.annualCtc", money(structure.getGrossSalary().multiply(BigDecimal.valueOf(12))));
        }
    }

    void putCandidateValues(Map<String, String> values, Candidate c) {
        values.put("candidate.fullName", c.getFullName());
        values.put("candidate.firstName", c.getFirstName());
        values.put("candidate.email", c.getEmail());
        if (c.getJobOpening() != null) {
            values.put("candidate.position", c.getJobOpening().getTitle());
            values.put("candidate.department", c.getJobOpening().getDepartment() != null ? c.getJobOpening().getDepartment().getName() : null);
        }
        values.put("candidate.offerAmount", c.getOfferAmount() != null ? money(BigDecimal.valueOf(c.getOfferAmount())) : null);
        values.put("candidate.joiningDate", c.getExpectedJoiningDate() != null ? DATE.format(c.getExpectedJoiningDate()) : null);
    }

    /** "INR 6,00,000.00" - Indian lakh/crore grouping (java.text ignores secondary grouping sizes, so done by hand). */
    static String money(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;
        String plain = value.abs().toPlainString();
        int dot = plain.indexOf('.');
        String integer = plain.substring(0, dot);
        String fraction = plain.substring(dot);
        StringBuilder grouped = new StringBuilder();
        if (integer.length() <= 3) {
            grouped.append(integer);
        } else {
            String head = integer.substring(0, integer.length() - 3);
            String tail = integer.substring(integer.length() - 3);
            int first = head.length() % 2;
            if (first > 0) grouped.append(head, 0, first);
            for (int i = first; i < head.length(); i += 2) {
                if (grouped.length() > 0) grouped.append(',');
                grouped.append(head, i, i + 2);
            }
            grouped.append(',').append(tail);
        }
        return "INR " + (negative ? "-" : "") + grouped + fraction;
    }

    // ------------------------------------------------------------------ helpers

    private LetterTemplate findTemplate(Long id) {
        return templateRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Letter template not found: " + id));
    }

    private IssuedLetter findLetter(Long id) {
        return issuedLetterRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Letter not found: " + id));
    }

    private static String fileName(String title, String suffix) {
        String base = (title + "-" + (suffix == null ? "" : suffix)).replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "").toLowerCase(Locale.ROOT);
        if (base.isBlank()) base = "letter";
        if (base.length() > 120) base = base.substring(0, 120);
        return base + ".pdf";
    }

    private static String companyName(Company company) {
        if (company == null) return "";
        if (company.getLegalName() != null && !company.getLegalName().isBlank()) return company.getLegalName();
        return company.getName() == null ? "" : company.getName();
    }

    private static String trimTo(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private Employee currentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return null;
        Long tenant = TenantContext.getCurrentTenant();
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .filter(e -> e.getCompany() != null && (tenant == null || tenant.equals(e.getCompany().getId())))
                .orElse(null);
    }

    private String currentActorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "system";
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .map(Employee::getFullName)
                .orElse(authentication.getName());
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
