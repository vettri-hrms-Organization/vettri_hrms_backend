package com.haodaone.letters.dto;

import com.haodaone.letters.entity.IssuedLetter;
import com.haodaone.letters.entity.LetterTemplate;
import com.haodaone.letters.entity.LetterheadSettings;
import com.haodaone.letters.service.LetterRenderer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request/response shapes for the letters API. */
public final class LetterDtos {

    private LetterDtos() {
    }

    public record TemplateDTO(Long id, String letterType, String name, String title, String body,
                              boolean builtIn, boolean active, List<String> customFields, LocalDateTime updatedAt) {
        public static TemplateDTO from(LetterTemplate t) {
            return new TemplateDTO(t.getId(), t.getLetterType(), t.getName(), t.getTitle(), t.getBody(),
                    t.isBuiltIn(), t.isActive(), LetterRenderer.customFieldsIn(t.getBody()), t.getUpdatedAt());
        }
    }

    public record TemplateRequest(String letterType, String name, String title, String body, Boolean active) {}

    public record LetterheadDTO(String companyName, String address, String signatoryName, String signatoryDesignation,
                                String footer, boolean hasLogo) {
        public static LetterheadDTO from(LetterheadSettings s, String companyName) {
            if (s == null) return new LetterheadDTO(companyName, null, null, null, null, false);
            return new LetterheadDTO(companyName, s.getAddress(), s.getSignatoryName(), s.getSignatoryDesignation(),
                    s.getFooter(), s.getLogoS3Key() != null && !s.getLogoS3Key().isBlank());
        }
    }

    public record LetterheadRequest(String address, String signatoryName, String signatoryDesignation, String footer) {}

    /** Exactly one of employeeId / candidateId. */
    public record GenerateRequest(Long templateId, Long employeeId, Long candidateId, Map<String, String> customFields,
                                  String title, Boolean sendEmail) {}

    public record PreviewResponse(String title, String body) {}

    public record PlaceholderDTO(String key, String group) {}

    public record IssuedLetterDTO(Long id, String letterType, String title, Long templateId, Long employeeId,
                                  String employeeName, Long candidateId, String fileName, Long fileSizeBytes,
                                  Long employeeDocumentId, LocalDateTime issuedAt, String issuedBy,
                                  LocalDateTime emailedAt, String emailStatus) {
        public static IssuedLetterDTO from(IssuedLetter l) {
            return new IssuedLetterDTO(l.getId(), l.getLetterType(), l.getTitle(),
                    l.getTemplate() != null ? l.getTemplate().getId() : null,
                    l.getEmployee() != null ? l.getEmployee().getId() : null,
                    l.getEmployee() != null ? l.getEmployee().getFullName() : null,
                    l.getCandidateId(), l.getFileName(), l.getFileSizeBytes(), l.getEmployeeDocumentId(),
                    l.getIssuedAt(), l.getIssuedBy(), l.getEmailedAt(), l.getEmailStatus());
        }
    }
}
