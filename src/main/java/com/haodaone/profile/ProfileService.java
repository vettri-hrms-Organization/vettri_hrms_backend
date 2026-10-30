package com.haodaone.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Keka-style employee self-service profile.
 *
 * <p>Employees edit their own contact, personal, emergency, family, education and experience details
 * directly. Identity (legal name, date of birth, gender) and bank/statutory details go through a
 * {@link ProfileChangeRequest} that HR approves; HR can edit those directly on other employees.
 * Nobody approves their own request, so HR staff changing their own bank details also need a colleague.</p>
 */
@Service
public class ProfileService {

    public static final String SECTION_IDENTITY = "IDENTITY";
    public static final String SECTION_BANK = "BANK";

    static final Set<String> BLOOD_GROUPS = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    static final Set<String> MARITAL_STATUSES = Set.of("SINGLE", "MARRIED", "DIVORCED", "WIDOWED", "SEPARATED");
    private static final List<String> IDENTITY_FIELDS = List.of("firstName", "lastName", "dateOfBirth", "gender");
    private static final List<String> BANK_FIELDS = List.of("bankName", "bankAccountNumber", "bankIfsc", "panNumber", "uanNumber");
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("firstName", "First name"), Map.entry("lastName", "Last name"),
            Map.entry("dateOfBirth", "Date of birth"), Map.entry("gender", "Gender"),
            Map.entry("bankName", "Bank name"), Map.entry("bankAccountNumber", "Account number"),
            Map.entry("bankIfsc", "IFSC"), Map.entry("panNumber", "PAN"), Map.entry("uanNumber", "UAN"));

    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final EmployeeFamilyMemberRepository family;
    private final EmployeeEducationRepository education;
    private final EmployeeExperienceRepository experience;
    private final ProfileChangeRequestRepository requests;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProfileService(EmployeeRepository employees, EmployeeService employeeService,
                          EmployeeFamilyMemberRepository family, EmployeeEducationRepository education,
                          EmployeeExperienceRepository experience, ProfileChangeRequestRepository requests,
                          AuditLogService auditLogService, NotificationService notificationService,
                          ObjectMapper objectMapper, Clock applicationClock) {
        this.employees = employees;
        this.employeeService = employeeService;
        this.family = family;
        this.education = education;
        this.experience = experience;
        this.requests = requests;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    // ------------------------------------------------------------------ read

    public record FamilyMemberDTO(Long id, String name, String relation, LocalDate dateOfBirth, String phone, boolean dependent) { }
    public record EducationDTO(Long id, String qualification, String institution, String specialization,
                               Integer startYear, Integer endYear, String grade) { }
    public record ExperienceDTO(Long id, String companyName, String jobTitle, String location,
                                LocalDate startDate, LocalDate endDate, String description) { }
    public record FieldChange(String field, String label, String from, String to) { }
    public record ChangeRequestDTO(Long id, Long employeeId, String employeeName, String employeeCode, String section,
                                   List<FieldChange> changes, String reason, String status, LocalDateTime requestedAt,
                                   String reviewedByName, String reviewNote, LocalDateTime reviewedAt) { }
    public record ProfileDTO(Long employeeId, String fullName, String firstName, String lastName, LocalDate dateOfBirth,
                             String gender, String workEmail, String phone, String personalEmail, String address,
                             String permanentAddress, String bloodGroup, String maritalStatus, String aboutMe,
                             String emergencyContactName, String emergencyContactRelation, String emergencyContactPhone,
                             String profilePhotoUrl, List<FamilyMemberDTO> family, List<EducationDTO> education,
                             List<ExperienceDTO> experience, List<ChangeRequestDTO> changeRequests) { }

    @Transactional(readOnly = true)
    public ProfileDTO profile(Long employeeId) {
        Employee e = employee(employeeId);
        return new ProfileDTO(e.getId(), e.getFullName(), e.getFirstName(), e.getLastName(), e.getDateOfBirth(),
                e.getGender(), e.getEmail(), e.getPhone(), e.getPersonalEmail(), e.getAddress(), e.getPermanentAddress(),
                e.getBloodGroup(), e.getMaritalStatus(), e.getAboutMe(), e.getEmergencyContactName(),
                e.getEmergencyContactRelation(), e.getEmergencyContactPhone(), e.getProfilePhotoUrl(),
                family.findAllByEmployeeIdAndDeletedFalseOrderByIdAsc(e.getId()).stream().map(ProfileService::toDto).toList(),
                education.findAllByEmployeeIdAndDeletedFalseOrderByEndYearDescIdDesc(e.getId()).stream().map(ProfileService::toDto).toList(),
                experience.findAllByEmployeeIdAndDeletedFalseOrderByStartDateDescIdDesc(e.getId()).stream().map(ProfileService::toDto).toList(),
                requests.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(e.getId()).stream().limit(10)
                        .map(r -> toDto(r, e)).toList());
    }

    // ------------------------------------------------------------------ direct edits

    /** Fields the employee edits without approval. Absent keys are left unchanged; blank clears. */
    public record DetailsRequest(String phone, String personalEmail, String address, String permanentAddress,
                                 String bloodGroup, String maritalStatus, String aboutMe, String emergencyContactName,
                                 String emergencyContactRelation, String emergencyContactPhone) { }

    @Transactional
    public ProfileDTO updateDetails(Long employeeId, DetailsRequest r) {
        Employee e = employee(employeeId);
        if (r.phone() != null) e.setPhone(phone(r.phone(), "Phone"));
        if (r.personalEmail() != null) e.setPersonalEmail(email(r.personalEmail()));
        if (r.address() != null) e.setAddress(text(r.address(), 300, "Current address"));
        if (r.permanentAddress() != null) e.setPermanentAddress(text(r.permanentAddress(), 300, "Permanent address"));
        if (r.bloodGroup() != null) e.setBloodGroup(oneOf(r.bloodGroup(), BLOOD_GROUPS, "Blood group"));
        if (r.maritalStatus() != null) e.setMaritalStatus(oneOf(upper(r.maritalStatus()), MARITAL_STATUSES, "Marital status"));
        if (r.aboutMe() != null) e.setAboutMe(text(r.aboutMe(), 1000, "About me"));
        if (r.emergencyContactName() != null) e.setEmergencyContactName(text(r.emergencyContactName(), 150, "Emergency contact name"));
        if (r.emergencyContactRelation() != null) e.setEmergencyContactRelation(text(r.emergencyContactRelation(), 40, "Relation"));
        if (r.emergencyContactPhone() != null) e.setEmergencyContactPhone(phone(r.emergencyContactPhone(), "Emergency contact phone"));
        employees.save(e);
        auditLogService.log("EMPLOYEE", e.getId(), "PROFILE_DETAILS_UPDATED", "Contact/personal details updated");
        return profile(employeeId);
    }

    public record FamilyMemberRequest(String name, String relation, LocalDate dateOfBirth, String phone, Boolean dependent) { }
    public record EducationRequest(String qualification, String institution, String specialization,
                                   Integer startYear, Integer endYear, String grade) { }
    public record ExperienceRequest(String companyName, String jobTitle, String location,
                                    LocalDate startDate, LocalDate endDate, String description) { }

    @Transactional
    public ProfileDTO saveFamilyMember(Long employeeId, Long id, FamilyMemberRequest r) {
        Employee e = employee(employeeId);
        EmployeeFamilyMember m = id == null ? newFamilyMember(e)
                : family.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Family member"));
        m.setName(required(r.name(), 150, "Name"));
        m.setRelation(required(r.relation(), 40, "Relation"));
        m.setDateOfBirth(pastDate(r.dateOfBirth(), "Date of birth"));
        m.setPhone(phone(r.phone(), "Phone"));
        m.setDependent(Boolean.TRUE.equals(r.dependent()));
        family.save(m);
        auditLogService.log("EMPLOYEE", e.getId(), id == null ? "PROFILE_FAMILY_ADDED" : "PROFILE_FAMILY_UPDATED", "Family member " + m.getRelation());
        return profile(employeeId);
    }

    @Transactional
    public ProfileDTO saveEducation(Long employeeId, Long id, EducationRequest r) {
        Employee e = employee(employeeId);
        EmployeeEducation row = id == null ? newEducation(e)
                : education.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Education entry"));
        row.setQualification(required(r.qualification(), 120, "Qualification"));
        row.setInstitution(required(r.institution(), 200, "Institution"));
        row.setSpecialization(text(r.specialization(), 150, "Specialization"));
        row.setGrade(text(r.grade(), 30, "Grade"));
        int maxYear = LocalDate.now(clock).getYear() + 6;
        row.setStartYear(year(r.startYear(), maxYear, "Start year"));
        row.setEndYear(year(r.endYear(), maxYear, "End year"));
        if (row.getStartYear() != null && row.getEndYear() != null && row.getEndYear() < row.getStartYear()) {
            throw new BadRequestException("End year can't be before the start year");
        }
        education.save(row);
        auditLogService.log("EMPLOYEE", e.getId(), id == null ? "PROFILE_EDUCATION_ADDED" : "PROFILE_EDUCATION_UPDATED", row.getQualification());
        return profile(employeeId);
    }

    @Transactional
    public ProfileDTO saveExperience(Long employeeId, Long id, ExperienceRequest r) {
        Employee e = employee(employeeId);
        EmployeeExperience row = id == null ? newExperience(e)
                : experience.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Experience entry"));
        row.setCompanyName(required(r.companyName(), 200, "Company"));
        row.setJobTitle(required(r.jobTitle(), 150, "Job title"));
        row.setLocation(text(r.location(), 150, "Location"));
        row.setDescription(text(r.description(), 1000, "Description"));
        row.setStartDate(pastDate(r.startDate(), "Start date"));
        row.setEndDate(pastDate(r.endDate(), "End date"));
        if (row.getStartDate() != null && row.getEndDate() != null && row.getEndDate().isBefore(row.getStartDate())) {
            throw new BadRequestException("End date can't be before the start date");
        }
        experience.save(row);
        auditLogService.log("EMPLOYEE", e.getId(), id == null ? "PROFILE_EXPERIENCE_ADDED" : "PROFILE_EXPERIENCE_UPDATED", row.getCompanyName());
        return profile(employeeId);
    }

    @Transactional
    public ProfileDTO deleteEntry(Long employeeId, String kind, Long id) {
        Employee e = employee(employeeId);
        switch (kind) {
            case "family" -> softDelete(family.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Family member")), family::save);
            case "education" -> softDelete(education.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Education entry")), education::save);
            case "experience" -> softDelete(experience.findByIdAndEmployeeIdAndDeletedFalse(id, e.getId()).orElseThrow(() -> notFound("Experience entry")), experience::save);
            default -> throw new BadRequestException("Unknown profile section");
        }
        auditLogService.log("EMPLOYEE", e.getId(), "PROFILE_" + kind.toUpperCase(Locale.ROOT) + "_REMOVED", "Entry " + id);
        return profile(employeeId);
    }

    // ------------------------------------------------------------------ approval-based changes

    /**
     * Identity or bank change. {@code applyDirectly} is true when HR edits another employee's record:
     * the change is applied at once (and audited). Otherwise a pending request goes to HR, replacing any
     * earlier pending request for the same section.
     */
    @Transactional
    public ProfileDTO requestChange(Long employeeId, String section, Map<String, String> values, String reason,
                                    boolean applyDirectly, Long actorUserId, String actorName) {
        Employee e = employee(employeeId);
        String normalizedSection = upper(section);
        List<String> allowed = SECTION_IDENTITY.equals(normalizedSection) ? IDENTITY_FIELDS
                : SECTION_BANK.equals(normalizedSection) ? BANK_FIELDS : null;
        if (allowed == null) throw new BadRequestException("Section must be IDENTITY or BANK");
        if (values == null || values.isEmpty()) throw new BadRequestException("Nothing to change");

        Map<String, Map<String, String>> changes = new LinkedHashMap<>();
        for (String field : allowed) {
            if (!values.containsKey(field)) continue;
            String to = normalize(field, values.get(field));
            String from = currentValue(e, field);
            if (!Objects.equals(emptyToNull(from), emptyToNull(to))) {
                Map<String, String> change = new LinkedHashMap<>();
                change.put("from", from);
                change.put("to", to);
                changes.put(field, change);
            }
        }
        if (changes.isEmpty()) throw new BadRequestException("These details are already up to date");
        // Validate now so the employee hears about a bad IFSC or PAN immediately, not after HR review.
        applyChanges(new Employee(), changes, true);

        if (applyDirectly) {
            applyChanges(e, changes, false);
            employees.save(e);
            auditLogService.log("EMPLOYEE", e.getId(), "PROFILE_" + normalizedSection + "_UPDATED_BY_HR",
                    "Updated by " + actorName + ": " + String.join(", ", changes.keySet()));
            return profile(employeeId);
        }

        requests.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(e.getId()).stream()
                .filter(r -> ProfileChangeRequest.PENDING.equals(r.getStatus()) && normalizedSection.equals(r.getSection()))
                .forEach(r -> { r.setStatus(ProfileChangeRequest.WITHDRAWN); requests.save(r); });

        ProfileChangeRequest request = new ProfileChangeRequest();
        request.setCompanyId(e.getCompany().getId());
        request.setEmployeeId(e.getId());
        request.setSection(normalizedSection);
        request.setChangesJson(writeJson(changes));
        request.setReason(text(reason, 500, "Reason"));
        ProfileChangeRequest saved = requests.save(request);
        auditLogService.log("PROFILE_CHANGE_REQUEST", saved.getId(), "SUBMITTED",
                e.getFullName() + " requested a " + sectionLabel(normalizedSection) + " change: " + String.join(", ", changes.keySet()));
        notificationService.notifyPermissionRecipients(e.getCompany().getId(), e.getId(), "EMPLOYEE_MANAGE",
                "PROFILE_CHANGE_REQUEST", "Profile change to review",
                e.getFullName() + " asked to update their " + sectionLabel(normalizedSection) + ".",
                "PROFILE_CHANGE_REQUEST", saved.getId());
        return profile(employeeId);
    }

    @Transactional
    public ProfileDTO withdraw(Long employeeId, Long requestId) {
        Employee e = employee(employeeId);
        ProfileChangeRequest r = requests.findByIdAndCompanyIdAndDeletedFalse(requestId, e.getCompany().getId())
                .filter(x -> x.getEmployeeId().equals(e.getId()))
                .orElseThrow(() -> notFound("Change request"));
        if (!ProfileChangeRequest.PENDING.equals(r.getStatus())) throw new BadRequestException("Only pending requests can be withdrawn");
        r.setStatus(ProfileChangeRequest.WITHDRAWN);
        requests.save(r);
        auditLogService.log("PROFILE_CHANGE_REQUEST", r.getId(), "WITHDRAWN", "Withdrawn by the employee");
        return profile(employeeId);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestDTO> pending() {
        Long companyId = tenant();
        List<ProfileChangeRequest> rows = requests.findAllByCompanyIdAndStatusAndDeletedFalseOrderByCreatedAtAsc(companyId, ProfileChangeRequest.PENDING);
        List<ChangeRequestDTO> out = new ArrayList<>();
        for (ProfileChangeRequest r : rows) {
            employees.findByIdAndCompany_IdAndDeletedFalse(r.getEmployeeId(), companyId).ifPresent(e -> out.add(toDto(r, e)));
        }
        return out;
    }

    @Transactional
    public ChangeRequestDTO review(Long requestId, boolean approve, String note, Long reviewerUserId, String reviewerName,
                                   Long reviewerEmployeeId) {
        Long companyId = tenant();
        ProfileChangeRequest r = requests.findByIdAndCompanyIdAndDeletedFalse(requestId, companyId)
                .orElseThrow(() -> notFound("Change request"));
        if (!ProfileChangeRequest.PENDING.equals(r.getStatus())) throw new BadRequestException("This request has already been " + r.getStatus().toLowerCase(Locale.ROOT));
        if (reviewerEmployeeId != null && reviewerEmployeeId.equals(r.getEmployeeId())) {
            throw new BadRequestException("You can't review your own profile change. Ask another HR admin.");
        }
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(r.getEmployeeId(), companyId).orElseThrow(() -> notFound("Employee"));
        if (approve) {
            applyChanges(e, readJson(r.getChangesJson()), false);
            employees.save(e);
        } else if (note == null || note.isBlank()) {
            throw new BadRequestException("Add a short note so the employee knows why");
        }
        r.setStatus(approve ? ProfileChangeRequest.APPROVED : ProfileChangeRequest.REJECTED);
        r.setReviewedByUserId(reviewerUserId);
        r.setReviewedByName(reviewerName);
        r.setReviewNote(text(note, 500, "Note"));
        r.setReviewedAt(LocalDateTime.now(clock));
        requests.save(r);
        auditLogService.log("PROFILE_CHANGE_REQUEST", r.getId(), approve ? "APPROVED" : "REJECTED",
                reviewerName + (approve ? " approved " : " declined ") + e.getFullName() + "'s " + sectionLabel(r.getSection()) + " change");
        if (e.getUser() != null) {
            notificationService.notifyUser(companyId, e.getUser().getId(), "PROFILE_CHANGE_REQUEST",
                    approve ? "Profile change approved" : "Profile change declined",
                    approve ? "Your " + sectionLabel(r.getSection()) + " have been updated."
                            : "Your " + sectionLabel(r.getSection()) + " change was declined: " + note.trim(),
                    "PROFILE_CHANGE_REQUEST", r.getId(), "NORMAL");
        }
        return toDto(r, e);
    }

    // ------------------------------------------------------------------ photo

    @Transactional
    public ProfileDTO setPhoto(Long employeeId, String publicUrl) {
        Employee e = employee(employeeId);
        e.setProfilePhotoUrl(publicUrl);
        employees.save(e);
        auditLogService.log("EMPLOYEE", e.getId(), publicUrl == null ? "PROFILE_PHOTO_REMOVED" : "PROFILE_PHOTO_UPDATED", "Profile photo");
        return profile(employeeId);
    }

    @Transactional(readOnly = true)
    public String currentPhoto(Long employeeId) {
        return employee(employeeId).getProfilePhotoUrl();
    }

    // ------------------------------------------------------------------ erasure

    /**
     * Privacy erasure (EmployeeAnonymisationService): clears the self-service fields on the entity
     * (caller saves it) and permanently deletes family, education, experience and change-request rows.
     */
    @Transactional
    public List<String> erasePersonalData(Employee e) {
        e.setPersonalEmail(null);
        e.setBloodGroup(null);
        e.setMaritalStatus(null);
        e.setPermanentAddress(null);
        e.setEmergencyContactRelation(null);
        e.setAboutMe(null);
        family.deleteAll(family.findAllByEmployeeIdAndDeletedFalseOrderByIdAsc(e.getId()));
        education.deleteAll(education.findAllByEmployeeIdAndDeletedFalseOrderByEndYearDescIdDesc(e.getId()));
        experience.deleteAll(experience.findAllByEmployeeIdAndDeletedFalseOrderByStartDateDescIdDesc(e.getId()));
        requests.deleteAll(requests.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(e.getId()));
        return List.of("personalEmail", "bloodGroup", "maritalStatus", "permanentAddress", "aboutMe",
                "familyMembers", "education", "experience", "profileChangeRequests");
    }

    // ------------------------------------------------------------------ helpers

    private void applyChanges(Employee target, Map<String, Map<String, String>> changes, boolean validateOnly) {
        CreateEmployeeRequest bank = new CreateEmployeeRequest();
        boolean anyBank = false;
        for (Map.Entry<String, Map<String, String>> entry : changes.entrySet()) {
            String to = entry.getValue().get("to");
            switch (entry.getKey()) {
                case "firstName" -> target.setFirstName(required(to, 100, "First name"));
                case "lastName" -> target.setLastName(required(to, 100, "Last name"));
                case "gender" -> target.setGender(text(to, 30, "Gender"));
                case "dateOfBirth" -> target.setDateOfBirth(birthDate(to));
                case "bankName" -> { bank.setBankName(to == null ? "" : to); anyBank = true; }
                case "bankAccountNumber" -> { bank.setBankAccountNumber(to == null ? "" : to); anyBank = true; }
                case "bankIfsc" -> { bank.setBankIfsc(to == null ? "" : to); anyBank = true; }
                case "panNumber" -> { bank.setPanNumber(to == null ? "" : to); anyBank = true; }
                case "uanNumber" -> { bank.setUanNumber(to == null ? "" : to); anyBank = true; }
                default -> throw new BadRequestException("Unknown field " + entry.getKey());
            }
        }
        // Same IFSC/PAN/UAN/account rules as the HR employee form.
        if (anyBank) employeeService.applyBankAndStatutoryDetails(target, bank);
    }

    private static String currentValue(Employee e, String field) {
        return switch (field) {
            case "firstName" -> e.getFirstName();
            case "lastName" -> e.getLastName();
            case "gender" -> e.getGender();
            case "dateOfBirth" -> e.getDateOfBirth() == null ? null : e.getDateOfBirth().toString();
            case "bankName" -> e.getBankName();
            case "bankAccountNumber" -> e.getBankAccountNumber();
            case "bankIfsc" -> e.getBankIfsc();
            case "panNumber" -> e.getPanNumber();
            case "uanNumber" -> e.getUanNumber();
            default -> null;
        };
    }

    private static String normalize(String field, String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return switch (field) {
            case "bankAccountNumber", "bankIfsc", "panNumber", "uanNumber" -> v.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            default -> v;
        };
    }

    private LocalDate birthDate(String value) {
        if (value == null || value.isBlank()) return null;
        LocalDate date;
        try {
            date = LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BadRequestException("Date of birth must be a valid date");
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today.minusYears(14)) || date.isBefore(today.minusYears(100))) {
            throw new BadRequestException("Please enter a valid date of birth");
        }
        return date;
    }

    private LocalDate pastDate(LocalDate value, String label) {
        if (value != null && value.isAfter(LocalDate.now(clock))) throw new BadRequestException(label + " can't be in the future");
        return value;
    }

    private static Integer year(Integer value, int max, String label) {
        if (value == null) return null;
        if (value < 1950 || value > max) throw new BadRequestException(label + " must be between 1950 and " + max);
        return value;
    }

    private static String required(String value, int max, String label) {
        String v = text(value, max, label);
        if (v == null) throw new BadRequestException(label + " is required");
        return v;
    }

    private static String text(String value, int max, String label) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException(label + " must be " + max + " characters or fewer");
        return v;
    }

    private static String phone(String value, String label) {
        String v = text(value, 20, label);
        if (v != null && !v.matches("^\\+?[0-9][0-9 ()-]{6,18}$")) {
            throw new BadRequestException(label + " must be a valid phone number");
        }
        return v;
    }

    private static String email(String value) {
        String v = text(value, 150, "Personal email");
        if (v != null && !v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("Personal email must be a valid email address");
        }
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    private static String oneOf(String value, Set<String> allowed, String label) {
        String v = text(value, 20, label);
        if (v != null && !allowed.contains(v)) throw new BadRequestException(label + " is not a valid option");
        return v;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String sectionLabel(String section) {
        return SECTION_BANK.equals(section) ? "bank and tax details" : "personal identity details";
    }

    private <T extends com.haodaone.common.entity.BaseEntity> void softDelete(T entity, Consumer<T> save) {
        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now(clock));
        save.accept(entity);
    }

    private EmployeeFamilyMember newFamilyMember(Employee e) {
        EmployeeFamilyMember m = new EmployeeFamilyMember();
        m.setCompanyId(e.getCompany().getId());
        m.setEmployeeId(e.getId());
        return m;
    }

    private EmployeeEducation newEducation(Employee e) {
        EmployeeEducation m = new EmployeeEducation();
        m.setCompanyId(e.getCompany().getId());
        m.setEmployeeId(e.getId());
        return m;
    }

    private EmployeeExperience newExperience(Employee e) {
        EmployeeExperience m = new EmployeeExperience();
        m.setCompanyId(e.getCompany().getId());
        m.setEmployeeId(e.getId());
        return m;
    }

    private Employee employee(Long employeeId) {
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(employeeId, tenant()).orElseThrow(() -> notFound("Employee"));
        if (e.getCompany() == null) throw new BadRequestException("Employee has no company");
        return e;
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }

    private static ResourceNotFoundException notFound(String what) {
        return new ResourceNotFoundException(what + " not found");
    }

    private String writeJson(Map<String, Map<String, String>> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not store the change request", ex);
        }
    }

    private Map<String, Map<String, String>> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, String>>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Stored change request is unreadable", ex);
        }
    }

    private ChangeRequestDTO toDto(ProfileChangeRequest r, Employee e) {
        List<FieldChange> changes = readJson(r.getChangesJson()).entrySet().stream()
                .map(x -> new FieldChange(x.getKey(), FIELD_LABELS.getOrDefault(x.getKey(), x.getKey()),
                        x.getValue().get("from"), x.getValue().get("to")))
                .toList();
        return new ChangeRequestDTO(r.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), r.getSection(), changes,
                r.getReason(), r.getStatus(), r.getCreatedAt(), r.getReviewedByName(), r.getReviewNote(), r.getReviewedAt());
    }

    private static FamilyMemberDTO toDto(EmployeeFamilyMember m) {
        return new FamilyMemberDTO(m.getId(), m.getName(), m.getRelation(), m.getDateOfBirth(), m.getPhone(), m.isDependent());
    }

    private static EducationDTO toDto(EmployeeEducation m) {
        return new EducationDTO(m.getId(), m.getQualification(), m.getInstitution(), m.getSpecialization(),
                m.getStartYear(), m.getEndYear(), m.getGrade());
    }

    private static ExperienceDTO toDto(EmployeeExperience m) {
        return new ExperienceDTO(m.getId(), m.getCompanyName(), m.getJobTitle(), m.getLocation(),
                m.getStartDate(), m.getEndDate(), m.getDescription());
    }
}
