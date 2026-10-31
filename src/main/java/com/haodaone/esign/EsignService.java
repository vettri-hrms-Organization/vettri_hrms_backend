package com.haodaone.esign;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.letters.entity.IssuedLetter;
import com.haodaone.letters.repository.IssuedLetterRepository;
import com.haodaone.letters.service.LetterStorageService;
import com.haodaone.performance.suite.PerfContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Click-to-sign for issued letters (offer acceptance, appointment, policies, NDAs issued as letters). HR sends a letter
 * for signature; the employee reads it and signs by typing their full name and agreeing to sign electronically.
 * The service records the SHA-256 of the exact PDF shown, the time, IP and browser, refuses to sign if the stored file no
 * longer matches that hash, and keeps a signed copy with a signature certificate page appended.
 * This is a simple electronic signature (IT Act s.3A-style click-wrap with an audit trail), not an Aadhaar eSign / DSC.
 */
@Service
public class EsignService {

    public static final String CONSENT = "I have read this document and agree to sign it electronically. I understand my typed name is my signature.";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH);

    private final SignatureRequestRepository requests;
    private final IssuedLetterRepository letters;
    private final LetterStorageService storage;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public EsignService(SignatureRequestRepository requests, IssuedLetterRepository letters, LetterStorageService storage, PerfContext ctx,
                        AuditLogService auditLogService, Clock applicationClock) {
        this.requests = requests;
        this.letters = letters;
        this.storage = storage;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record SendRequest(Long letterId, String message, LocalDate dueDate) { }
    public record SignRequest(String fullName, Boolean consent) { }
    public record Signer(String ip, String userAgent) { }
    public record RequestDTO(Long id, Long employeeId, String employeeName, String title, String sourceType, Long sourceId, String status,
                             String message, String requestedByName, LocalDateTime requestedAt, LocalDate dueDate, LocalDateTime viewedAt,
                             String signedName, LocalDateTime signedAt, String signerIp, String documentSha256, String signedSha256,
                             String declineReason, boolean canSign, String consentText) { }
    public record FileDownload(byte[] bytes, String fileName) { }

    @Transactional
    public RequestDTO send(SendRequest r, String actor) {
        requireAdmin();
        if (r == null || r.letterId() == null) throw new BadRequestException("Choose the letter to send");
        IssuedLetter letter = letters.findByIdAndCompany_IdAndDeletedFalse(r.letterId(), ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Letter not found"));
        if (letter.getEmployee() == null) throw new BadRequestException("Only letters issued to an employee can be signed here");
        boolean open = requests.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), letter.getEmployee().getId()).stream()
                .anyMatch(x -> "LETTER".equals(x.getSourceType()) && letter.getId().equals(x.getSourceId())
                        && (SignatureRequest.PENDING.equals(x.getStatus()) || SignatureRequest.SIGNED.equals(x.getStatus())));
        if (open) throw new BadRequestException("This letter is already out for signature or signed");
        SignatureRequest x = new SignatureRequest();
        x.setCompanyId(ctx.companyId());
        x.setEmployeeId(letter.getEmployee().getId());
        x.setTitle(letter.getTitle() == null ? "Letter" : letter.getTitle());
        x.setSourceType("LETTER");
        x.setSourceId(letter.getId());
        x.setDocumentSha256(sha256(storage.get(letter.getS3ObjectKey())));
        x.setStatus(SignatureRequest.PENDING);
        x.setMessage(PerfContext.text(r.message(), 1000));
        x.setDueDate(r.dueDate());
        x.setRequestedByName(actor);
        x = requests.save(x);
        ctx.notify(letter.getEmployee(), "ESIGN", "Please sign: " + x.getTitle(), "HR sent you a document to review and sign.", "SIGNATURE_REQUEST", x.getId());
        auditLogService.log("SIGNATURE_REQUEST", x.getId(), "SENT", actor + " sent '" + x.getTitle() + "' for signature (sha256 " + x.getDocumentSha256() + ")");
        return toDto(x);
    }

    @Transactional(readOnly = true)
    public List<RequestDTO> list() {
        requireAdmin();
        return requests.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestDTO> mine() {
        Employee me = ctx.me();
        return requests.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), me.getId()).stream()
                .filter(x -> !SignatureRequest.CANCELLED.equals(x.getStatus())).map(this::toDto).toList();
    }

    /** The document to read before signing (or the signed copy afterwards). Marks the request viewed. */
    @Transactional
    public FileDownload document(Long id) {
        SignatureRequest x = visible(id);
        Employee me = ctx.meOrNull();
        if (me != null && me.getId().equals(x.getEmployeeId()) && x.getViewedAt() == null) {
            x.setViewedAt(LocalDateTime.now(clock));
            requests.save(x);
        }
        if (x.getSignedObjectKey() != null) return new FileDownload(storage.get(x.getSignedObjectKey()), safe(x.getTitle()) + " - signed.pdf");
        return new FileDownload(storage.get(letter(x).getS3ObjectKey()), safe(x.getTitle()) + ".pdf");
    }

    @Transactional
    public RequestDTO sign(Long id, SignRequest r, Signer signer) {
        Employee me = ctx.me();
        SignatureRequest x = visible(id);
        if (!me.getId().equals(x.getEmployeeId())) throw new AccessDeniedException("Only the addressee can sign this document");
        if (!SignatureRequest.PENDING.equals(x.getStatus())) throw new BadRequestException("This document is not waiting for your signature");
        if (r == null || !Boolean.TRUE.equals(r.consent())) throw new BadRequestException("Tick the box to agree to sign electronically");
        String typed = PerfContext.text(r.fullName(), 150);
        if (typed == null || !normalise(typed).equals(normalise(me.getFullName())))
            throw new BadRequestException("Type your full name exactly as it appears in your profile: " + me.getFullName());
        byte[] original = storage.get(letter(x).getS3ObjectKey());
        if (!sha256(original).equals(x.getDocumentSha256())) throw new BadRequestException("The document changed after it was sent. Ask HR to send it again.");
        LocalDateTime now = LocalDateTime.now(clock);
        x.setSignedName(typed);
        x.setSignedAt(now);
        x.setSignerIp(signer == null ? null : trim(signer.ip(), 64));
        x.setSignerUserAgent(signer == null ? null : trim(signer.userAgent(), 400));
        byte[] signed = stamp(original, x, me);
        String key = "esign/" + x.getCompanyId() + "/" + UUID.randomUUID() + ".pdf";
        storage.put(key, signed, "application/pdf");
        x.setSignedObjectKey(key);
        x.setSignedSha256(sha256(signed));
        x.setStatus(SignatureRequest.SIGNED);
        requests.save(x);
        auditLogService.log("SIGNATURE_REQUEST", x.getId(), "SIGNED", me.getFullName() + " signed '" + x.getTitle() + "' from " + x.getSignerIp());
        return toDto(x);
    }

    @Transactional
    public RequestDTO decline(Long id, String reason) {
        Employee me = ctx.me();
        SignatureRequest x = visible(id);
        if (!me.getId().equals(x.getEmployeeId())) throw new AccessDeniedException("Not your document");
        if (!SignatureRequest.PENDING.equals(x.getStatus())) throw new BadRequestException("This document is not waiting for your signature");
        String why = PerfContext.text(reason, 500);
        if (why == null) throw new BadRequestException("Tell HR why you're declining");
        x.setStatus(SignatureRequest.DECLINED);
        x.setDeclineReason(why);
        requests.save(x);
        auditLogService.log("SIGNATURE_REQUEST", x.getId(), "DECLINED", me.getFullName() + " declined '" + x.getTitle() + "'");
        return toDto(x);
    }

    @Transactional
    public RequestDTO cancel(Long id, String actor) {
        requireAdmin();
        SignatureRequest x = visible(id);
        if (!SignatureRequest.PENDING.equals(x.getStatus())) throw new BadRequestException("Only pending requests can be cancelled");
        x.setStatus(SignatureRequest.CANCELLED);
        requests.save(x);
        auditLogService.log("SIGNATURE_REQUEST", x.getId(), "CANCELLED", actor + " cancelled the signature request");
        return toDto(x);
    }

    // ------------------------------------------------------------------ helpers

    /** Appends a signature certificate page to the original PDF. */
    byte[] stamp(byte[] original, SignatureRequest x, Employee signer) {
        try (PDDocument doc = Loader.loadPDF(original); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780;
                y = line(cs, bold, 16, 50, y, "Electronic signature certificate");
                y -= 10;
                String[][] rows = {
                        {"Document", x.getTitle()},
                        {"Signed by", x.getSignedName() + " (" + signer.getEmployeeCode() + ")"},
                        {"Signed at", x.getSignedAt().format(STAMP) + " IST"},
                        {"IP address", Objects.toString(x.getSignerIp(), "not recorded")},
                        {"Browser", Objects.toString(x.getSignerUserAgent(), "not recorded")},
                        {"Sent by", Objects.toString(x.getRequestedByName(), "HR") + " on " + x.getCreatedAt().format(STAMP)},
                        {"Document SHA-256", x.getDocumentSha256()},
                        {"Request ID", "ESIGN-" + x.getId()}};
                for (String[] row : rows) {
                    line(cs, bold, 10, 50, y, row[0]);
                    for (String part : wrap(ascii(row[1]), 70)) {
                        y = line(cs, regular, 10, 170, y, part);
                    }
                    y -= 4;
                }
                y -= 10;
                for (String part : wrap("Consent given: " + CONSENT, 95)) y = line(cs, regular, 9, 50, y, part);
                y -= 6;
                for (String part : wrap("The SHA-256 above identifies the exact pages before this certificate. Recompute it on the original to verify nothing was changed.", 95)) {
                    y = line(cs, regular, 9, 50, y, part);
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not stamp the signed document", ex);
        }
    }

    private static float line(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws java.io.IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(ascii(text));
        cs.endText();
        return y - size - 5;
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        String rest = text == null ? "" : text;
        while (rest.length() > width) {
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut).trim();
        }
        out.add(rest);
        return out;
    }

    /** Standard-14 fonts only cover Latin-1; replace anything else so stamping never fails on a name or browser string. */
    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c >= 32 && c < 127 ? c : '?');
        return b.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static String safe(String s) {
        return s == null ? "document" : s.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
    }

    private IssuedLetter letter(SignatureRequest x) {
        return letters.findByIdAndCompany_IdAndDeletedFalse(x.getSourceId(), x.getCompanyId()).orElseThrow(() -> new BadRequestException("The letter was removed"));
    }

    private boolean isAdmin() {
        return ctx.hasOrgScope("LETTER_MANAGE");
    }

    private void requireAdmin() {
        if (!isAdmin()) throw new AccessDeniedException("Only HR can manage signature requests");
    }

    private SignatureRequest visible(Long id) {
        SignatureRequest x = requests.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        Employee me = ctx.meOrNull();
        if (!isAdmin() && (me == null || !me.getId().equals(x.getEmployeeId()))) throw new ResourceNotFoundException("Document not found");
        return x;
    }

    private RequestDTO toDto(SignatureRequest x) {
        Employee me = ctx.meOrNull();
        String name = PerfContext.name(ctx.people(List.of(x.getEmployeeId())), x.getEmployeeId());
        boolean canSign = me != null && me.getId().equals(x.getEmployeeId()) && SignatureRequest.PENDING.equals(x.getStatus());
        return new RequestDTO(x.getId(), x.getEmployeeId(), name, x.getTitle(), x.getSourceType(), x.getSourceId(), x.getStatus(), x.getMessage(),
                x.getRequestedByName(), x.getCreatedAt(), x.getDueDate(), x.getViewedAt(), x.getSignedName(), x.getSignedAt(), x.getSignerIp(),
                x.getDocumentSha256(), x.getSignedSha256(), x.getDeclineReason(), canSign, CONSENT);
    }
}
