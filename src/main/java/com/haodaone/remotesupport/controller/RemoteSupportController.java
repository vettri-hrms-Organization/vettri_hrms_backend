package com.haodaone.remotesupport.controller;
import com.haodaone.remotesupport.dto.RemoteSupportDTO;
import com.haodaone.remotesupport.entity.RemoteSupportOperation;
import com.haodaone.remotesupport.service.RemoteSupportService;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/remote-support")
@PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('REMOTE_SUPPORT_MANAGE')")
public class RemoteSupportController {
    private final RemoteSupportService service;
    public RemoteSupportController(RemoteSupportService service){this.service=service;}
    @PostMapping("/configure") @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public ResponseEntity<RemoteSupportDTO.Response> configure(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.CONFIGURE,user.getId()));}
    @PostMapping("/detect") @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public ResponseEntity<RemoteSupportDTO.Response> detect(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.DETECT,user.getId()));}
    @PostMapping("/rotate") @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public ResponseEntity<RemoteSupportDTO.Response> rotate(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.ROTATE,user.getId()));}
    @PostMapping("/disable") @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public RemoteSupportDTO.Response disable(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return service.disable(deviceId,user.getId());}
    @GetMapping @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public List<RemoteSupportDTO.Response> list(@PathVariable Long deviceId){return service.list(deviceId);}
    @GetMapping("/{jobId}") @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)") public RemoteSupportDTO.Response get(@PathVariable Long deviceId,@PathVariable Long jobId){return service.get(deviceId,jobId);}
    @PostMapping("/{jobId}/credential")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @companySecurity.canManageRemoteSupportDevice(#deviceId)")
    public ResponseEntity<RemoteSupportDTO.CredentialResponse> revealCredential(@PathVariable Long deviceId, @PathVariable Long jobId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.revealCredential(deviceId, jobId));
    }
}