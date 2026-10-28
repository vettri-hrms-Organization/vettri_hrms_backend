package com.haodaone.privacy.service;

import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.LegalDocument;
import com.haodaone.privacy.entity.LegalDocumentType;
import com.haodaone.privacy.repository.LegalDocumentRepository;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Current legal document versions. The version strings and URLs come from
 * configuration (app.legal.*) so publishing a new Terms/Privacy/DPA is a
 * config change: bump e.g. app.legal.terms-version and every user who must
 * accept TERMS is asked to re-accept on their next login/refresh (UserDTO
 * legalAcceptanceRequired + the frontend gate in ProtectedRoute).
 *
 * Who must accept what:
 *  - every company user (account holder): PRIVACY + ACCEPTABLE_USE
 *  - COMPANY_ADMIN additionally: TERMS + DPA (DPA on behalf of the company)
 *  - SUPER_ADMIN / users without a company: nothing
 * MONITORING_NOTICE is not a login gate; it is accepted per employee through
 * MonitoringConsentService before a device is enrolled.
 */
@Service
public class LegalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    private final LegalDocumentRepository documentRepository;

    @Value("${app.legal.terms-url:https://vettrihrms.in/terms}")
    private String termsUrl = "https://vettrihrms.in/terms";
    @Value("${app.legal.terms-version:2026-10-01}")
    private String termsVersion = "2026-10-01";
    @Value("${app.legal.privacy-url:https://vettrihrms.in/privacy}")
    private String privacyUrl = "https://vettrihrms.in/privacy";
    @Value("${app.legal.privacy-version:2026-10-01}")
    private String privacyVersion = "2026-10-01";
    @Value("${app.legal.dpa-url:https://vettrihrms.in/dpa}")
    private String dpaUrl = "https://vettrihrms.in/dpa";
    @Value("${app.legal.dpa-version:2026-10-01}")
    private String dpaVersion = "2026-10-01";
    @Value("${app.legal.acceptable-use-url:https://vettrihrms.in/terms#acceptable-use}")
    private String acceptableUseUrl = "https://vettrihrms.in/terms#acceptable-use";
    @Value("${app.legal.acceptable-use-version:2026-10-01}")
    private String acceptableUseVersion = "2026-10-01";
    @Value("${app.legal.monitoring-notice-url:https://vettrihrms.in/privacy#employee-monitoring}")
    private String monitoringNoticeUrl = "https://vettrihrms.in/privacy#employee-monitoring";
    @Value("${app.legal.monitoring-notice-version:2026-10-01}")
    private String monitoringNoticeVersion = "2026-10-01";
    /** Turns the re-acceptance login gate on/off (acceptances are still recorded when off). */
    @Value("${app.legal.acceptance-enforced:true}")
    private boolean acceptanceEnforced = true;

    public LegalDocumentService(LegalDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Map<LegalDocumentType, PrivacyDTO.LegalDocumentInfo> currentDocuments() {
        Map<LegalDocumentType, PrivacyDTO.LegalDocumentInfo> docs = new EnumMap<>(LegalDocumentType.class);
        docs.put(LegalDocumentType.TERMS, info(LegalDocumentType.TERMS, termsVersion, termsUrl));
        docs.put(LegalDocumentType.PRIVACY, info(LegalDocumentType.PRIVACY, privacyVersion, privacyUrl));
        docs.put(LegalDocumentType.DPA, info(LegalDocumentType.DPA, dpaVersion, dpaUrl));
        docs.put(LegalDocumentType.ACCEPTABLE_USE, info(LegalDocumentType.ACCEPTABLE_USE, acceptableUseVersion, acceptableUseUrl));
        docs.put(LegalDocumentType.MONITORING_NOTICE,
                info(LegalDocumentType.MONITORING_NOTICE, monitoringNoticeVersion, monitoringNoticeUrl));
        return docs;
    }

    public PrivacyDTO.LegalDocumentInfo current(LegalDocumentType type) {
        return currentDocuments().get(type);
    }

    public String currentVersion(LegalDocumentType type) {
        return current(type).version();
    }

    public boolean isAcceptanceEnforced() {
        return acceptanceEnforced;
    }

    /** Documents this user must have accepted (current version) to use the app. */
    public Set<LegalDocumentType> requiredFor(User user) {
        Set<LegalDocumentType> required = new LinkedHashSet<>();
        if (user == null || user.getCompany() == null) {
            return required;
        }
        Set<String> roles = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role != null && role.getName() != null) roles.add(role.getName());
            }
        }
        if (roles.contains("SUPER_ADMIN")) {
            return required;
        }
        if (roles.contains("COMPANY_ADMIN")) {
            required.add(LegalDocumentType.TERMS);
        }
        required.add(LegalDocumentType.PRIVACY);
        required.add(LegalDocumentType.ACCEPTABLE_USE);
        if (roles.contains("COMPANY_ADMIN")) {
            required.add(LegalDocumentType.DPA);
        }
        return required;
    }

    @Transactional(readOnly = true)
    public List<PrivacyDTO.LegalDocumentHistory> history() {
        List<PrivacyDTO.LegalDocumentHistory> result = new ArrayList<>();
        for (LegalDocument doc : documentRepository.findAllByDeletedFalseOrderByDocTypeAscPublishedAtDesc()) {
            result.add(new PrivacyDTO.LegalDocumentHistory(doc.getDocType(), doc.getTitle(), doc.getDocVersion(),
                    doc.getUrl(), doc.getPublishedAt(), doc.isCurrent()));
        }
        return result;
    }

    /** Records each configured version in the legal_document registry (idempotent). */
    @EventListener(ApplicationReadyEvent.class)
    public void syncRegistry() {
        try {
            for (PrivacyDTO.LegalDocumentInfo doc : currentDocuments().values()) {
                LegalDocument row = documentRepository
                        .findByDocTypeAndDocVersionAndDeletedFalse(doc.type(), doc.version())
                        .orElse(null);
                if (row != null && row.isCurrent() && doc.url().equals(row.getUrl())) {
                    continue;
                }
                for (LegalDocument other : documentRepository.findAllByDocTypeAndDeletedFalse(doc.type())) {
                    if (other.isCurrent() && (row == null || !other.getId().equals(row.getId()))) {
                        other.setCurrent(false);
                        documentRepository.save(other);
                    }
                }
                if (row == null) {
                    row = new LegalDocument();
                    row.setDocType(doc.type());
                    row.setDocVersion(doc.version());
                    row.setPublishedAt(LocalDateTime.now());
                }
                row.setTitle(doc.title());
                row.setUrl(doc.url());
                row.setCurrent(true);
                documentRepository.save(row);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not sync the legal document registry: {}", ex.getMessage());
        }
    }

    private static PrivacyDTO.LegalDocumentInfo info(LegalDocumentType type, String version, String url) {
        String safeVersion = version == null || version.isBlank() ? "1" : version.trim();
        String safeUrl = url == null || url.isBlank() ? "https://vettrihrms.in" : url.trim();
        return new PrivacyDTO.LegalDocumentInfo(type.name(), type.title(), safeVersion, safeUrl);
    }
}
