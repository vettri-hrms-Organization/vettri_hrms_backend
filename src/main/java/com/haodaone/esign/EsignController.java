package com.haodaone.esign;

import com.haodaone.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Click-to-sign: HR (LETTER_MANAGE org-wide) sends issued letters for signature; employees read and sign their own. */
@RestController
@RequestMapping("/api/esign")
@PreAuthorize("isAuthenticated()")
public class EsignController {

    private final EsignService service;

    public EsignController(EsignService service) {
        this.service = service;
    }

    @GetMapping
    public List<EsignService.RequestDTO> list() { return service.list(); }

    @GetMapping("/mine")
    public List<EsignService.RequestDTO> mine() { return service.mine(); }

    @PostMapping
    public EsignService.RequestDTO send(@RequestBody EsignService.SendRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.send(r, name(p));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        EsignService.FileDownload f = service.document(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .body(f.bytes());
    }

    /** Whether the caller has a saved signature. */
    @GetMapping("/my-signature/info")
    public EsignService.SignatureInfo mySignatureInfo() { return service.mySignatureInfo(); }

    /** The caller's saved signature picture (PNG), 404 when none is saved. */
    @GetMapping("/my-signature")
    public ResponseEntity<byte[]> mySignature() {
        byte[] png = service.mySignatureImage();
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control", "no-store").body(png);
    }

    /** Save or replace the caller's signature from an uploaded photo or a drawing (source=UPLOAD|DRAWN). */
    @PutMapping(value = "/my-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EsignService.SignatureInfo saveMySignature(@RequestParam("file") MultipartFile file, @RequestParam(value = "source", required = false) String source) {
        return service.saveMySignature(file, source);
    }

    @DeleteMapping("/my-signature")
    public void deleteMySignature() { service.deleteMySignature(); }

    @PostMapping("/{id}/sign")
    public EsignService.RequestDTO sign(@PathVariable Long id, @RequestBody EsignService.SignRequest r, HttpServletRequest request) {
        return service.sign(id, r, new EsignService.Signer(clientIp(request), request.getHeader("User-Agent")));
    }

    @PostMapping("/{id}/decline")
    public EsignService.RequestDTO decline(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.decline(id, body.get("reason"));
    }

    @PostMapping("/{id}/cancel")
    public EsignService.RequestDTO cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.cancel(id, name(p));
    }

    /** First X-Forwarded-For hop (set by the reverse proxy in production), else the socket address. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "HR";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
