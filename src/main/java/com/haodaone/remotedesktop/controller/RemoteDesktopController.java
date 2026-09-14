package com.haodaone.remotedesktop.controller;

import com.haodaone.remotedesktop.dto.RemoteDesktopDTO;
import com.haodaone.remotedesktop.service.RemoteDesktopService;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/remote-desktop")
@PreAuthorize("hasAuthority('MONITORING_MANAGE')")
public class RemoteDesktopController {
    private final RemoteDesktopService service;
    private final com.haodaone.remotedesktop.service.RemoteDesktopSignalingService signalingService;
    public RemoteDesktopController(RemoteDesktopService service, com.haodaone.remotedesktop.service.RemoteDesktopSignalingService signalingService) { this.service = service; this.signalingService = signalingService; }
    @PostMapping("/sessions") public ResponseEntity<RemoteDesktopDTO.Session> start(@PathVariable Long deviceId, @AuthenticationPrincipal CustomUserPrincipal principal) { return ResponseEntity.status(201).body(service.start(deviceId, principal.getId())); }
    @GetMapping("/sessions") public java.util.List<RemoteDesktopDTO.Session> list(@PathVariable Long deviceId) { return service.list(deviceId); }
    @GetMapping("/sessions/{sessionId}") public RemoteDesktopDTO.Session get(@PathVariable Long deviceId, @PathVariable Long sessionId) { return service.get(deviceId, sessionId); }
    @PostMapping("/sessions/{sessionId}/end") public RemoteDesktopDTO.Session end(@PathVariable Long deviceId, @PathVariable Long sessionId) { return service.end(deviceId, sessionId); }
    @PostMapping("/sessions/{sessionId}/input") public ResponseEntity<Void> input(@PathVariable Long deviceId, @PathVariable Long sessionId, @RequestBody RemoteDesktopDTO.InputEvent input) { service.enqueueInput(deviceId, sessionId, input); return ResponseEntity.accepted().build(); }
    @PostMapping("/sessions/{sessionId}/webrtc/offer") public ResponseEntity<Void> offer(@PathVariable Long deviceId, @PathVariable Long sessionId, @RequestHeader("X-WebRTC-Signaling-Token") String token, @RequestBody RemoteDesktopDTO.WebRtcSignal signal) { signalingService.browserOffer(deviceId, sessionId, token, signal); return ResponseEntity.accepted().build(); }
    @PostMapping("/sessions/{sessionId}/webrtc/ice") public ResponseEntity<Void> ice(@PathVariable Long deviceId, @PathVariable Long sessionId, @RequestHeader("X-WebRTC-Signaling-Token") String token, @RequestBody RemoteDesktopDTO.WebRtcSignal signal) { signalingService.browserIce(deviceId, sessionId, token, signal); return ResponseEntity.accepted().build(); }
    @GetMapping("/sessions/{sessionId}/events") public List<RemoteDesktopDTO.WebRtcEvent> events(@PathVariable Long deviceId, @PathVariable Long sessionId, @RequestHeader("X-WebRTC-Signaling-Token") String token) { return signalingService.browserEvents(deviceId, sessionId, token); }
    @GetMapping(value = "/sessions/{sessionId}/frame", produces = MediaType.IMAGE_JPEG_VALUE) public ResponseEntity<byte[]> frame(@PathVariable Long deviceId, @PathVariable Long sessionId) { var frame = service.latestFrameInfo(deviceId, sessionId); return ResponseEntity.ok().contentType(service.frameType()).cacheControl(CacheControl.noStore()).header("X-Remote-Width", String.valueOf(frame.width())).header("X-Remote-Height", String.valueOf(frame.height())).body(frame.bytes()); }
}