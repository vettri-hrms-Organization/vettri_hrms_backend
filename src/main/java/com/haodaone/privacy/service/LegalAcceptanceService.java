package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.LegalAcceptance;
import com.haodaone.privacy.entity.LegalDocumentType;
import com.haodaone.privacy.repository.LegalAcceptanceRepository;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records acceptance of Vettri's legal documents and works out which
 * documents a user still has to (re-)accept. Acceptance rows are evidence
 * and are never updated or deleted by the application.
 */
@Service
public class LegalAcceptanceService {

    private final LegalAcceptanceRepository acceptanceRepository;
    private final LegalDocumentService documentService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public LegalAcceptanceService(LegalAcceptanceRepository acceptanceRepository, LegalDocumentService documentService,
                                  UserRepository userRepository, AuditLogService auditLogService) {
        this.acceptanceRepository = acceptanceRepository;
        this.documentService = documentService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    /** Documents the user must still accept at their current version (empty when nothing is pending). */
    @Transactional(readOnly = true)
    public List<PrivacyDTO.LegalDocumentInfo> pendingFor(User user) {
        List<PrivacyDTO.LegalDocumentInfo> pending = new ArrayList<>();
        if (user == null || user.getId() == null) {
            return pending;
        }
        Map<LegalDocumentType, PrivacyDTO.LegalDocumentInfo> current = documentService.currentDocuments();
        for (LegalDocumentType type : documentService.requiredFor(user)) {
            PrivacyDTO.LegalDocumentInfo doc = current.get(type);
            if (!acceptanceRepository.existsByUser_IdAndDocTypeAndDocVersionAndDeletedFalse(
                    user.getId(), type.name(), doc.version())) {
                pending.add(doc);
            }
        }
        return pending;
    }

    /**
     * Sets legalAcceptanceRequired / pendingLegalDocuments on a UserDTO (login, refresh, /me).
     * Must run inside an open transaction or with an initialised user (roles are read).
     */
    public void applyTo(UserDTO dto, User user) {
        if (dto == null || user == null) {
            return;
        }
        List<String> pending = pendingFor(user).stream().map(PrivacyDTO.LegalDocumentInfo::type).toList();
        dto.setPendingLegalDocuments(pending);
        dto.setLegalAcceptanceRequired(documentService.isAcceptanceEnforced() && !pending.isEmpty());
    }

    @Transactional(readOnly = true)
    public void applyTo(UserDTO dto, Long userId) {
        if (dto == null || userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> applyTo(dto, user));
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.LegalStatus status(Long userId) {
        User user = loadUser(userId);
        List<PrivacyDTO.LegalDocumentInfo> pending = pendingFor(user);
        return new PrivacyDTO.LegalStatus(new ArrayList<>(documentService.currentDocuments().values()), pending,
                documentService.isAcceptanceEnforced() && !pending.isEmpty(), isCompanyAdmin(user));
    }

    /**
     * Accepts the given document types at their current version. The DPA can
     * only be accepted by a company admin (on behalf of the company).
     */
    @Transactional
    public PrivacyDTO.LegalStatus accept(Long userId, List<String> documentTypes, HttpServletRequest httpRequest) {
        User user = loadUser(userId);
        if (documentTypes == null || documentTypes.isEmpty()) {
            throw new BadRequestException("Select the documents you accept.");
        }
        Set<LegalDocumentType> types = new LinkedHashSet<>();
        for (String raw : documentTypes) {
            LegalDocumentType type = LegalDocumentType.parse(raw);
            if (type == null || type == LegalDocumentType.MONITORING_NOTICE) {
                throw new BadRequestException("Unknown legal document: " + raw);
            }
            types.add(type);
        }
        if (types.contains(LegalDocumentType.DPA) && !isCompanyAdmin(user)) {
            throw new BadRequestException("Only an organization administrator can accept the Data Processing Agreement on behalf of the organization.");
        }
        for (LegalDocumentType type : types) {
            record(user, user.getCompany(), type, httpRequest);
        }
        return status(userId);
    }

    /**
     * Signup hook (RegistrationService): the new company admin accepted the
     * Terms (incl. Acceptable Use) and Privacy Policy, and the DPA on behalf
     * of the new company.
     */
    @Transactional
    public void recordSignupAcceptance(User user, Company company, HttpServletRequest httpRequest) {
        record(user, company, LegalDocumentType.TERMS, httpRequest);
        record(user, company, LegalDocumentType.PRIVACY, httpRequest);
        record(user, company, LegalDocumentType.ACCEPTABLE_USE, httpRequest);
        record(user, company, LegalDocumentType.DPA, httpRequest);
    }

    @Transactional(readOnly = true)
    public List<PrivacyDTO.AcceptanceView> myAcceptances(Long userId) {
        return acceptanceRepository.findAllByUser_IdAndDeletedFalseOrderByAcceptedAtDesc(userId).stream()
                .map(LegalAcceptanceService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<PrivacyDTO.AcceptanceView> companyAcceptances(Long companyId) {
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return acceptanceRepository.findAllByCompany_IdAndDeletedFalseOrderByAcceptedAtDesc(companyId).stream()
                .map(LegalAcceptanceService::toView).toList();
    }

    private void record(User user, Company company, LegalDocumentType type, HttpServletRequest httpRequest) {
        String version = documentService.currentVersion(type);
        if (user.getId() != null && acceptanceRepository.existsByUser_IdAndDocTypeAndDocVersionAndDeletedFalse(
                user.getId(), type.name(), version)) {
            return;
        }
        LegalAcceptance acceptance = new LegalAcceptance();
        acceptance.setUser(user);
        acceptance.setCompany(company);
        acceptance.setDocType(type.name());
        acceptance.setDocVersion(version);
        acceptance.setAcceptedAt(LocalDateTime.now());
        acceptance.setOnBehalfOfCompany(type == LegalDocumentType.DPA);
        if (httpRequest != null) {
            acceptance.setIpAddress(truncate(httpRequest.getRemoteAddr(), 64));
            acceptance.setUserAgent(truncate(httpRequest.getHeader("User-Agent"), 500));
        }
        LegalAcceptance saved = acceptanceRepository.save(acceptance);
        auditLogService.log("LegalAcceptance", saved.getId(), "ACCEPT",
                "User " + user.getId() + " accepted " + type.name() + " version " + version
                        + (type == LegalDocumentType.DPA ? " on behalf of the company" : ""));
    }

    private User loadUser(Long userId) {
        if (userId == null) {
            throw new BadRequestException("Authentication required");
        }
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    static boolean isCompanyAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role != null && "COMPANY_ADMIN".equals(role.getName()));
    }

    private static PrivacyDTO.AcceptanceView toView(LegalAcceptance a) {
        User user = a.getUser();
        return new PrivacyDTO.AcceptanceView(a.getId(), user == null ? null : user.getId(),
                user == null ? null : user.getFullName(), user == null ? null : user.getEmail(),
                a.getDocType(), a.getDocVersion(), a.getAcceptedAt(), a.getIpAddress(), a.getUserAgent(),
                a.isOnBehalfOfCompany());
    }

    static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
