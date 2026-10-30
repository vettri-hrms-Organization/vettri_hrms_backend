package com.haodaone.profile;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CompanySecurity;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Employee self-service profile. The employee (or HR with EMPLOYEE_MANAGE on them) edits contact,
 * personal, emergency, family, education, experience and photo directly. Identity and bank details are
 * requested by the employee and approved by HR; HR editing someone else applies them at once.
 */
@RestController
public class ProfileController {

    private static final String SELF_OR_MANAGER =
            "@employeeSecurity.isSelf(#employeeId) or (hasAuthority('EMPLOYEE_MANAGE') and @companySecurity.canManageEmployee(#employeeId))";

    private final ProfileService service;
    private final ProfilePhotoStorageService photos;
    private final CompanySecurity companySecurity;
    private final EmployeeRepository employees;

    public ProfileController(ProfileService service, ProfilePhotoStorageService photos,
                             CompanySecurity companySecurity, EmployeeRepository employees) {
        this.service = service;
        this.photos = photos;
        this.companySecurity = companySecurity;
        this.employees = employees;
    }

    @GetMapping("/api/profile/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('EMPLOYEE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public ProfileService.ProfileDTO profile(@PathVariable Long employeeId) {
        return service.profile(employeeId);
    }

    @PatchMapping("/api/profile/{employeeId}/details")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO updateDetails(@PathVariable Long employeeId, @RequestBody ProfileService.DetailsRequest request) {
        return service.updateDetails(employeeId, request);
    }

    public record ChangeRequestBody(String section, Map<String, String> values, String reason) { }

    @PostMapping("/api/profile/{employeeId}/change-requests")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO requestChange(@PathVariable Long employeeId, @RequestBody ChangeRequestBody body,
                                                   @AuthenticationPrincipal CustomUserPrincipal principal) {
        // HR edits other employees directly; their own identity/bank details still need a colleague's approval.
        boolean applyDirectly = !isSelf(principal, employeeId) && companySecurity.canManageEmployee(employeeId);
        return service.requestChange(employeeId, body.section(), body.values(), body.reason(), applyDirectly,
                principal.getId(), displayName(principal));
    }

    @PostMapping("/api/profile/{employeeId}/change-requests/{requestId}/withdraw")
    @PreAuthorize("@employeeSecurity.isSelf(#employeeId)")
    public ProfileService.ProfileDTO withdraw(@PathVariable Long employeeId, @PathVariable Long requestId) {
        return service.withdraw(employeeId, requestId);
    }

    @GetMapping("/api/profile/change-requests/pending")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public List<ProfileService.ChangeRequestDTO> pending() {
        return service.pending();
    }

    public record ReviewBody(String note) { }

    @PostMapping("/api/profile/change-requests/{requestId}/approve")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ProfileService.ChangeRequestDTO approve(@PathVariable Long requestId, @RequestBody(required = false) ReviewBody body,
                                                   @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(requestId, true, body == null ? null : body.note(), principal.getId(), displayName(principal), ownEmployeeId(principal));
    }

    @PostMapping("/api/profile/change-requests/{requestId}/reject")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ProfileService.ChangeRequestDTO reject(@PathVariable Long requestId, @RequestBody(required = false) ReviewBody body,
                                                  @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(requestId, false, body == null ? null : body.note(), principal.getId(), displayName(principal), ownEmployeeId(principal));
    }

    @PostMapping("/api/profile/{employeeId}/family")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO addFamily(@PathVariable Long employeeId, @RequestBody ProfileService.FamilyMemberRequest r) {
        return service.saveFamilyMember(employeeId, null, r);
    }

    @PutMapping("/api/profile/{employeeId}/family/{id}")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO updateFamily(@PathVariable Long employeeId, @PathVariable Long id, @RequestBody ProfileService.FamilyMemberRequest r) {
        return service.saveFamilyMember(employeeId, id, r);
    }

    @PostMapping("/api/profile/{employeeId}/education")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO addEducation(@PathVariable Long employeeId, @RequestBody ProfileService.EducationRequest r) {
        return service.saveEducation(employeeId, null, r);
    }

    @PutMapping("/api/profile/{employeeId}/education/{id}")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO updateEducation(@PathVariable Long employeeId, @PathVariable Long id, @RequestBody ProfileService.EducationRequest r) {
        return service.saveEducation(employeeId, id, r);
    }

    @PostMapping("/api/profile/{employeeId}/experience")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO addExperience(@PathVariable Long employeeId, @RequestBody ProfileService.ExperienceRequest r) {
        return service.saveExperience(employeeId, null, r);
    }

    @PutMapping("/api/profile/{employeeId}/experience/{id}")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO updateExperience(@PathVariable Long employeeId, @PathVariable Long id, @RequestBody ProfileService.ExperienceRequest r) {
        return service.saveExperience(employeeId, id, r);
    }

    @DeleteMapping("/api/profile/{employeeId}/{kind}/{id}")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO deleteEntry(@PathVariable Long employeeId, @PathVariable String kind, @PathVariable Long id) {
        return service.deleteEntry(employeeId, kind, id);
    }

    public record PhotoBody(String image) { }

    @PutMapping("/api/profile/{employeeId}/photo")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO uploadPhoto(@PathVariable Long employeeId, @RequestBody PhotoBody body) {
        if (body == null || body.image() == null) throw new BadRequestException("Choose a photo to upload");
        String previous = service.currentPhoto(employeeId);
        String url = photos.store(body.image());
        ProfileService.ProfileDTO result = service.setPhoto(employeeId, url);
        photos.deleteQuietly(previous);
        return result;
    }

    @DeleteMapping("/api/profile/{employeeId}/photo")
    @PreAuthorize(SELF_OR_MANAGER)
    public ProfileService.ProfileDTO removePhoto(@PathVariable Long employeeId) {
        String previous = service.currentPhoto(employeeId);
        ProfileService.ProfileDTO result = service.setPhoto(employeeId, null);
        photos.deleteQuietly(previous);
        return result;
    }

    /** Public by design (see ProfilePhotoStorageService): the unguessable file name is the access token. */
    @GetMapping("/api/profile-photos/{name:.+}")
    public ResponseEntity<byte[]> photo(@PathVariable String name) {
        byte[] bytes = photos.read(name);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ProfilePhotoStorageService.contentType(name)))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    private boolean isSelf(CustomUserPrincipal principal, Long employeeId) {
        Long own = ownEmployeeId(principal);
        return own != null && own.equals(employeeId);
    }

    private Long ownEmployeeId(CustomUserPrincipal principal) {
        if (principal == null) return null;
        return employees.findByUser_IdAndDeletedFalse(principal.getId()).map(e -> e.getId()).orElse(null);
    }

    private static String displayName(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "HR";
        String name = principal.getUser().getFullName();
        return name == null || name.isBlank() ? principal.getUsername() : name;
    }
}
