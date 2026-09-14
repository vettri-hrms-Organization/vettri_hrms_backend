package com.haodaone.remotecommand.controller;

import com.haodaone.remotecommand.dto.RemoteCommandDTO;
import com.haodaone.remotecommand.service.RemoteCommandService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceId}/remote-commands")
@PreAuthorize("hasAuthority('MONITORING_MANAGE')")
public class RemoteCommandController {
    private final RemoteCommandService service;
    public RemoteCommandController(RemoteCommandService service) { this.service = service; }
    @PostMapping public ResponseEntity<RemoteCommandDTO.Response> create(@PathVariable Long deviceId, @Valid @RequestBody RemoteCommandDTO.CreateRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) { return ResponseEntity.status(201).body(service.create(deviceId, request, principal.getId())); }
    @GetMapping public java.util.List<RemoteCommandDTO.Response> list(@PathVariable Long deviceId) { return service.list(deviceId); }
    @GetMapping("/{id}") public RemoteCommandDTO.Response get(@PathVariable Long deviceId, @PathVariable Long id) { return service.get(deviceId, id); }
    @PostMapping("/{id}/cancel") public RemoteCommandDTO.Response cancel(@PathVariable Long deviceId, @PathVariable Long id) { return service.cancel(deviceId, id); }
}