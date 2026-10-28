package com.haodaone.letters.controller;

import com.haodaone.letters.dto.LetterDtos;
import com.haodaone.letters.entity.LetterType;
import com.haodaone.letters.service.LetterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/letters")
public class LetterController {

    private static final String MANAGE =
            "hasAuthority('LETTER_MANAGE') and @authorizationService.hasOrganizationScope('LETTER_MANAGE')";
    private static final String MANAGE_OR_RECRUITER = MANAGE
            + " or (hasAuthority('RECRUITMENT_MANAGE') and @authorizationService.hasOrganizationScope('RECRUITMENT_MANAGE'))";

    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    // ---------------------------------------------------------------- templates

    @GetMapping("/templates")
    @PreAuthorize(MANAGE)
    public List<LetterDtos.TemplateDTO> templates() {
        return letterService.listTemplates();
    }

    /** Active offer templates - for the candidate "Generate offer letter" action (recruiters too). */
    @GetMapping("/offer-templates")
    @PreAuthorize(MANAGE_OR_RECRUITER)
    public List<LetterDtos.TemplateDTO> offerTemplates() {
        return letterService.listTemplates().stream()
                .filter(t -> t.active() && LetterType.OFFER.equals(t.letterType()))
                .toList();
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public LetterDtos.TemplateDTO template(@PathVariable Long id) {
        return letterService.getTemplate(id);
    }

    @PostMapping("/templates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<LetterDtos.TemplateDTO> createTemplate(@RequestBody LetterDtos.TemplateRequest request) {
        return ResponseEntity.status(201).body(letterService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public LetterDtos.TemplateDTO updateTemplate(@PathVariable Long id, @RequestBody LetterDtos.TemplateRequest request) {
        return letterService.updateTemplate(id, request);
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        letterService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/placeholders")
    @PreAuthorize(MANAGE_OR_RECRUITER)
    public List<LetterDtos.PlaceholderDTO> placeholders() {
        return letterService.placeholders();
    }

    // ---------------------------------------------------------------- letterhead

    @GetMapping("/letterhead")
    @PreAuthorize(MANAGE)
    public LetterDtos.LetterheadDTO letterhead() {
        return letterService.getLetterhead();
    }

    @PutMapping("/letterhead")
    @PreAuthorize(MANAGE)
    public LetterDtos.LetterheadDTO updateLetterhead(@RequestBody LetterDtos.LetterheadRequest request) {
        return letterService.updateLetterhead(request);
    }

    @PostMapping(value = "/letterhead/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public LetterDtos.LetterheadDTO uploadLogo(@RequestPart("file") MultipartFile file) {
        return letterService.uploadLogo(file);
    }

    @DeleteMapping("/letterhead/logo")
    @PreAuthorize(MANAGE)
    public LetterDtos.LetterheadDTO removeLogo() {
        return letterService.removeLogo();
    }

    @GetMapping("/letterhead/logo")
    @PreAuthorize(MANAGE)
    public ResponseEntity<byte[]> logo() {
        Object[] logo = letterService.logo();
        if (logo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType((String) logo[1]))
                .body((byte[]) logo[0]);
    }

    // ---------------------------------------------------------------- generation

    @PostMapping("/preview")
    @PreAuthorize(MANAGE)
    public LetterDtos.PreviewResponse preview(@RequestBody LetterDtos.GenerateRequest request) {
        return letterService.preview(request);
    }

    @PostMapping("/generate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<LetterDtos.IssuedLetterDTO> generate(@RequestBody LetterDtos.GenerateRequest request) {
        return ResponseEntity.status(201).body(letterService.generateForEmployee(request));
    }

    @PostMapping("/candidates/{candidateId}/offer-letter/preview")
    @PreAuthorize(MANAGE_OR_RECRUITER)
    public LetterDtos.PreviewResponse previewOffer(@PathVariable Long candidateId,
                                                   @RequestBody(required = false) LetterDtos.GenerateRequest request) {
        return letterService.preview(new LetterDtos.GenerateRequest(
                request == null ? null : request.templateId(), null, candidateId,
                request == null ? null : request.customFields(), request == null ? null : request.title(), false));
    }

    @PostMapping("/candidates/{candidateId}/offer-letter")
    @PreAuthorize(MANAGE_OR_RECRUITER)
    public ResponseEntity<LetterDtos.IssuedLetterDTO> generateOffer(@PathVariable Long candidateId,
                                                                   @RequestBody(required = false) LetterDtos.GenerateRequest request) {
        return ResponseEntity.status(201).body(letterService.generateOfferLetter(candidateId, request));
    }

    @GetMapping("/candidates/{candidateId}")
    @PreAuthorize(MANAGE_OR_RECRUITER)
    public List<LetterDtos.IssuedLetterDTO> candidateLetters(@PathVariable Long candidateId) {
        return letterService.listForCandidate(candidateId);
    }

    // ---------------------------------------------------------------- issued letters

    @GetMapping
    @PreAuthorize(MANAGE)
    public List<LetterDtos.IssuedLetterDTO> recent() {
        return letterService.listRecent();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<LetterDtos.IssuedLetterDTO> mine() {
        return letterService.listMine();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("(" + MANAGE + ") or @employeeSecurity.isSelf(#employeeId)")
    public List<LetterDtos.IssuedLetterDTO> forEmployee(@PathVariable Long employeeId) {
        return letterService.listForEmployee(employeeId);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("(" + MANAGE + ") or @employeeSecurity.isSelf(@letterService.resolveEmployeeIdForLetter(#id))")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        LetterService.Download download = letterService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName().replace("\"", "") + "\"")
                .body(download.bytes());
    }

    @PostMapping("/{id}/email")
    @PreAuthorize(MANAGE)
    public LetterDtos.IssuedLetterDTO email(@PathVariable Long id) {
        return letterService.email(id);
    }
}
