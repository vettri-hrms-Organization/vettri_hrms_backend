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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
 * longer matches that hash, and keeps a signed copy with a signature certificate page appended. Employees save a
 * signature picture once (uploaded photo or drawn on screen); it is fetched and stamped on every document they sign.
 * This is a simple electronic signature (IT Act s.3A-style click-wrap with an audit trail), not an Aadhaar eSign / DSC.
 */
@Service
public class EsignService {

    public static final String CONSENT = "I have read this document and agree to sign it electronically. My saved signature and typed name are my signature.";
    static final int MAX_SIGNATURE_BYTES = 3 * 1024 * 1024;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH);

    private final SignatureRequestRepository requests;
    private final EmployeeSignatureRepository signatures;
    private final IssuedLetterRepository letters;
    private final LetterStorageService storage;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public EsignService(SignatureRequestRepository requests, EmployeeSignatureRepository signatures, IssuedLetterRepository letters,
                        LetterStorageService storage, PerfContext ctx, AuditLogService auditLogService, Clock applicationClock) {
        this.requests = requests;
        this.signatures = signatures;
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
    public record SignatureInfo(boolean saved, String source, LocalDateTime updatedAt) { }

    // ------------------------------------------------------------------ my saved signature

    @Transactional(readOnly = true)
    public SignatureInfo mySignatureInfo() {
        Employee me = ctx.me();
        return signatures.findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId())
                .map(sig -> new SignatureInfo(true, sig.getSource(), sig.getUpdatedAt()))
                .orElse(new SignatureInfo(false, null, null));
    }

    /** The caller's saved signature as PNG, or null when none is saved. */
    @Transactional(readOnly = true)
    public byte[] mySignatureImage() {
        Employee me = ctx.me();
        return signatures.findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId())
                .map(sig -> storage.get(sig.getS3ObjectKey())).orElse(null);
    }

    /**
     * Saves the caller's signature from a photo or a drawing. The picture is cleaned before it is kept: paper-white
     * background made transparent, cropped to the ink, scaled to at most 900px wide and re-encoded as PNG, so a phone
     * photo of a signature on paper works and nothing but pixels is stored.
     */
    @Transactional
    public SignatureInfo saveMySignature(MultipartFile file, String source) {
        Employee me = ctx.me();
        if (file == null || file.isEmpty()) throw new BadRequestException("Choose a picture of your signature");
        if (file.getSize() > MAX_SIGNATURE_BYTES) throw new BadRequestException("The picture is too large - keep it under 3 MB");
        byte[] png;
        try {
            png = cleanSignature(file.getBytes());
        } catch (java.io.IOException ex) {
            throw new BadRequestException("We couldn't read that picture. Use a PNG or JPG photo of your signature.");
        }
        String kind = EmployeeSignature.DRAWN.equalsIgnoreCase(source) ? EmployeeSignature.DRAWN : EmployeeSignature.UPLOAD;
        String key = "employee-documents/" + ctx.companyId() + "/" + me.getId() + "/SIGNATURE/" + UUID.randomUUID() + ".png";
        storage.put(key, png, "image/png");
        EmployeeSignature sig = signatures.findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId())
                .orElseGet(EmployeeSignature::new);
        sig.setCompanyId(ctx.companyId());
        sig.setEmployeeId(me.getId());
        sig.setS3ObjectKey(key);
        sig.setContentSha256(sha256(png));
        sig.setSource(kind);
        sig = signatures.save(sig);
        auditLogService.log("EMPLOYEE_SIGNATURE", sig.getId(), "SAVED", me.getFullName() + " saved a " + (EmployeeSignature.DRAWN.equals(kind) ? "drawn" : "uploaded") + " signature");
        return new SignatureInfo(true, kind, LocalDateTime.now(clock));
    }

    @Transactional
    public void deleteMySignature() {
        Employee me = ctx.me();
        signatures.findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId()).ifPresent(sig -> {
            sig.setDeleted(true);
            signatures.save(sig);
        });
    }

    private static int luminance(int argb) {
        return (((argb >> 16) & 0xff) * 299 + ((argb >> 8) & 0xff) * 587 + (argb & 0xff) * 114) / 1000;
    }

    static byte[] cleanSignature(byte[] bytes) throws java.io.IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) throw new BadRequestException("We couldn't read that picture. Use a PNG or JPG photo of your signature.");
        if (src.getWidth() < 40 || src.getHeight() < 20) throw new BadRequestException("The picture is too small - use a clearer photo");
        if ((long) src.getWidth() * src.getHeight() > 40_000_000L) throw new BadRequestException("The picture is too large - crop it to the signature");
        int w = src.getWidth(), h = src.getHeight();
        // Photos are often taken in dim light, so the paper is grey, not white. Measure this picture's paper
        // (the bright 80th percentile of its pixels) and treat only pixels clearly darker than it as ink.
        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                if (((argb >>> 24) & 0xff) <= 40) continue;
                histogram[luminance(argb)]++;
            }
        }
        int counted = 0, total = java.util.Arrays.stream(histogram).sum(), paper = 255;
        for (int l = 0; l < 256; l++) {
            counted += histogram[l];
            if (counted >= total * 0.8) { paper = l; break; }
        }
        int threshold = Math.max(40, Math.min(paper - 45, Math.round(paper * 0.72f)));
        int minX = w, minY = h, maxX = -1, maxY = -1;
        BufferedImage ink = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                int lum = luminance(argb);
                if (a <= 40 || lum >= threshold) continue;
                // Ink keeps its hue but is deepened, and fades in near the threshold so strokes have smooth edges.
                int r = ((argb >> 16) & 0xff) * 6 / 10, g = ((argb >> 8) & 0xff) * 6 / 10, b = (argb & 0xff) * 6 / 10;
                int alpha = Math.min(a, Math.min(255, (threshold - lum) * 255 / Math.max(1, threshold / 3)));
                if (alpha < 30) continue;
                ink.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0 || (maxX - minX) < 20 || (maxY - minY) < 8)
            throw new BadRequestException("We couldn't find a signature in that picture. Sign with a dark pen on white paper, or draw it instead.");
        int pad = 8;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);
        BufferedImage cropped = ink.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        int outW = Math.min(900, cropped.getWidth());
        int outH = Math.max(1, Math.round(cropped.getHeight() * (outW / (float) cropped.getWidth())));
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(cropped, 0, 0, outW, outH, null);
        g2.dispose();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(out, "png", buf);
        return buf.toByteArray();
    }

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
        EmployeeSignature sig = signatures.findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId())
                .orElseThrow(() -> new BadRequestException("Add your signature first: upload a photo of it or draw it."));
        byte[] signatureImage = storage.get(sig.getS3ObjectKey());
        byte[] original = storage.get(letter(x).getS3ObjectKey());
        if (!sha256(original).equals(x.getDocumentSha256())) throw new BadRequestException("The document changed after it was sent. Ask HR to send it again.");
        x.setSignatureImageKey(sig.getS3ObjectKey());
        x.setSignatureImageSha256(sig.getContentSha256());
        LocalDateTime now = LocalDateTime.now(clock);
        x.setSignedName(typed);
        x.setSignedAt(now);
        x.setSignerIp(signer == null ? null : trim(signer.ip(), 64));
        x.setSignerUserAgent(signer == null ? null : trim(signer.userAgent(), 400));
        byte[] signed = stamp(original, x, me, signatureImage);
        // Same storage location as the employee's other letters, so the bucket's existing permissions cover it.
        String key = storage.employeeLetterKey(x.getCompanyId(), x.getEmployeeId());
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

    /** Appends an "accepted and signed" page to the original PDF: the signature picture, then the audit details. */
    byte[] stamp(byte[] original, SignatureRequest x, Employee signer, byte[] signatureImage) {
        try (PDDocument doc = Loader.loadPDF(original); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780;
                y = line(cs, bold, 16, 50, y, "Accepted and signed");
                y = line(cs, regular, 10, 50, y, "I accept " + x.getTitle() + ".");
                y -= 8;
                if (signatureImage != null) {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, signatureImage, "signature");
                    float maxW = 220, maxH = 90;
                    float scale = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
                    float iw = img.getWidth() * scale, ih = img.getHeight() * scale;
                    cs.drawImage(img, 50, y - ih, iw, ih);
                    y -= ih + 6;
                }
                cs.setLineWidth(0.6f);
                cs.moveTo(50, y);
                cs.lineTo(290, y);
                cs.stroke();
                y -= 14;
                y = line(cs, bold, 11, 50, y, x.getSignedName());
                y = line(cs, regular, 10, 50, y, x.getSignedAt().format(STAMP) + " IST");
                y -= 24;
                y = line(cs, bold, 13, 50, y, "Electronic signature certificate");
                y -= 6;
                String[][] rows = {
                        {"Document", x.getTitle()},
                        {"Signed by", x.getSignedName() + " (" + signer.getEmployeeCode() + ")"},
                        {"Signed at", x.getSignedAt().format(STAMP) + " IST"},
                        {"IP address", Objects.toString(x.getSignerIp(), "not recorded")},
                        {"Browser", Objects.toString(x.getSignerUserAgent(), "not recorded")},
                        {"Sent by", Objects.toString(x.getRequestedByName(), "HR") + " on " + x.getCreatedAt().format(STAMP)},
                        {"Document SHA-256", x.getDocumentSha256()},
                        {"Signature SHA-256", Objects.toString(x.getSignatureImageSha256(), "none")},
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
