package com.haodaone.remotesupport.controller;
import com.haodaone.remotesupport.dto.RemoteSupportDTO;
import com.haodaone.remotesupport.entity.RemoteSupportOperation;
import com.haodaone.remotesupport.service.RemoteSupportService;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/remote-support")
@PreAuthorize("hasAuthority('MONITORING_MANAGE')")
public class RemoteSupportController {
    private final RemoteSupportService service;
    public RemoteSupportController(RemoteSupportService service){this.service=service;}
    @PostMapping("/configure") public ResponseEntity<RemoteSupportDTO.Response> configure(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.CONFIGURE,user.getId()));}
    @PostMapping("/detect") public ResponseEntity<RemoteSupportDTO.Response> detect(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.DETECT,user.getId()));}
    @PostMapping("/rotate") public ResponseEntity<RemoteSupportDTO.Response> rotate(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return ResponseEntity.status(201).body(service.request(deviceId,RemoteSupportOperation.ROTATE,user.getId()));}
    @PostMapping("/disable") public RemoteSupportDTO.Response disable(@PathVariable Long deviceId,@AuthenticationPrincipal CustomUserPrincipal user){return service.disable(deviceId,user.getId());}
    @GetMapping public List<RemoteSupportDTO.Response> list(@PathVariable Long deviceId){return service.list(deviceId);}
    @GetMapping("/{jobId}") public RemoteSupportDTO.Response get(@PathVariable Long deviceId,@PathVariable Long jobId){return service.get(deviceId,jobId);}
}