package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.*;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.service.AgentIngestService;
import com.haodaone.software.dto.AgentSoftwareJobDTO;
import com.haodaone.software.dto.AgentSoftwareStatusRequest;
import com.haodaone.software.service.SoftwareManagementService;
import com.haodaone.remotecommand.dto.RemoteCommandDTO;
import com.haodaone.remotecommand.service.RemoteCommandService;
import com.haodaone.remotedesktop.dto.RemoteDesktopDTO;
import com.haodaone.remotedesktop.service.RemoteDesktopService;
import com.haodaone.remotedesktop.service.RemoteDesktopSignalingService;
import com.haodaone.remotesupport.dto.RemoteSupportDTO;
import com.haodaone.remotesupport.service.RemoteSupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Device-facing endpoints called by HaodaOne.Agent/Services/
 * ApiClientService.cs - paths ("agent/heartbeat", "agent/activity/batch")
 * and payload shapes are figuixed by that client, not ours to rename.
 * Authenticated by security.AgentTokenAuthenticationFilter (a per-device
 * static bearer token, distinct from the user JWT scheme) rather than
 * permitAll - unlike attendance.controller.AdmsController's biometric
 * devices, this agent CAN attach a bearer token, so we use real auth
 * instead of a network-restriction-only posture.
 *
 * Every response is wrapped in AgentEnvelope to match what ApiClientService
 * deserializes as {@code ApiEnvelope<T>} and reads via {@code envelope?.Data}.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentIngestService agentIngestService;
    private final SoftwareManagementService softwareManagementService;
    private final RemoteCommandService remoteCommandService;
    private final RemoteDesktopService remoteDesktopService;
    private final RemoteDesktopSignalingService remoteDesktopSignalingService;
    private final RemoteSupportService remoteSupportService;

    public AgentController(AgentIngestService agentIngestService, SoftwareManagementService softwareManagementService, RemoteCommandService remoteCommandService, RemoteDesktopService remoteDesktopService, RemoteDesktopSignalingService remoteDesktopSignalingService, RemoteSupportService remoteSupportService) {
        this.agentIngestService = agentIngestService;
        this.softwareManagementService = softwareManagementService;
        this.remoteCommandService = remoteCommandService;
        this.remoteDesktopService = remoteDesktopService;
        this.remoteDesktopSignalingService = remoteDesktopSignalingService;
        this.remoteSupportService = remoteSupportService;
    }

    @PostMapping("/heartbeat")
    public AgentEnvelope<HeartbeatResponseData> heartbeat(@AuthenticationPrincipal MonitoredDevice device,
                                                            @Valid @RequestBody HeartbeatRequest request,
                                                            HttpServletRequest servletRequest) {
        HeartbeatResponseData data = agentIngestService.recordHeartbeat(device, request, servletRequest.getRemoteAddr());
        return AgentEnvelope.ok(data);
    }

    @PostMapping("/activity/batch")
    public AgentEnvelope<ActivityBatchResponseData> activityBatch(@AuthenticationPrincipal MonitoredDevice device,
                                                                    @Valid @RequestBody ActivityBatchRequest request,
                                                                    HttpServletRequest servletRequest) {
        ActivityBatchResponseData data = agentIngestService.recordActivityBatch(device, request, servletRequest.getRemoteAddr());
        return AgentEnvelope.ok(data);
    }

    @GetMapping("/software/jobs")
    public AgentEnvelope<java.util.List<AgentSoftwareJobDTO>> softwareJobs(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(softwareManagementService.getAgentJobs(device));
    }

    @PostMapping("/software/jobs/{targetId}/status")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> softwareJobStatus(
            @AuthenticationPrincipal MonitoredDevice device,
            @PathVariable Long targetId,
            @RequestBody AgentSoftwareStatusRequest request) {
        softwareManagementService.updateAgentJobStatus(device, targetId, request);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "Status recorded"));
    }

    @GetMapping("/remote-commands/jobs")
    public AgentEnvelope<java.util.List<RemoteCommandDTO.AgentJob>> remoteCommandJobs(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(remoteCommandService.agentJobs(device));
    }

    @GetMapping("/remote-commands/cancelled")
    public AgentEnvelope<java.util.List<Long>> cancelledRemoteCommandJobs(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(remoteCommandService.cancelledAgentJobs(device));
    }

    @PostMapping("/remote-commands/jobs/{id}/result")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteCommandResult(@AuthenticationPrincipal MonitoredDevice device,
                                                                                   @PathVariable Long id,
                                                                                   @RequestBody RemoteCommandDTO.AgentResult result) {
        remoteCommandService.updateAgentJob(device, id, result);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "Result recorded"));
    }

    @GetMapping("/remote-desktop/sessions")
    public AgentEnvelope<java.util.List<RemoteDesktopDTO.AgentSession>> remoteDesktopSessions(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(remoteDesktopService.agentRequests(device));
    }

    @PostMapping("/remote-desktop/frames")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteDesktopFrame(@AuthenticationPrincipal MonitoredDevice device, @RequestBody RemoteDesktopDTO.AgentFrame frame) {
        remoteDesktopService.receiveFrame(device, frame);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "Frame recorded"));
    }

    @GetMapping("/remote-desktop/input")
    public AgentEnvelope<java.util.List<RemoteDesktopDTO.AgentInput>> remoteDesktopInput(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(remoteDesktopService.agentInputs(device));
    }

    @GetMapping("/remote-desktop/webrtc/events")
    public AgentEnvelope<java.util.List<RemoteDesktopDTO.WebRtcEvent>> remoteDesktopWebRtcEvents(@AuthenticationPrincipal MonitoredDevice device) {
        return AgentEnvelope.ok(remoteDesktopSignalingService.agentEvents(device));
    }

    @PostMapping("/remote-desktop/webrtc/answer")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteDesktopWebRtcAnswer(@AuthenticationPrincipal MonitoredDevice device, @RequestBody RemoteDesktopDTO.WebRtcSignal signal) {
        remoteDesktopSignalingService.agentAnswer(device, signal);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "WebRTC answer recorded"));
    }

    @PostMapping("/remote-desktop/webrtc/ice")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteDesktopWebRtcIce(@AuthenticationPrincipal MonitoredDevice device, @RequestBody RemoteDesktopDTO.WebRtcSignal signal) {
        remoteDesktopSignalingService.agentIce(device, signal);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "WebRTC ICE recorded"));
    }

    @PostMapping("/remote-desktop/webrtc/state")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteDesktopWebRtcState(@AuthenticationPrincipal MonitoredDevice device, @RequestBody RemoteDesktopDTO.WebRtcSignal signal) {
        remoteDesktopSignalingService.agentState(device, signal);
        return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "WebRTC state recorded"));
    }

    @GetMapping("/remote-support/jobs")
    public AgentEnvelope<java.util.List<RemoteSupportDTO.AgentJob>> remoteSupportJobs(@AuthenticationPrincipal MonitoredDevice device) { return AgentEnvelope.ok(remoteSupportService.agentJobs(device)); }

    @PostMapping("/remote-support/jobs/{id}/result")
    public AgentEnvelope<com.haodaone.software.dto.AgentAck> remoteSupportResult(@AuthenticationPrincipal MonitoredDevice device, @PathVariable Long id, @RequestBody RemoteSupportDTO.AgentResult result) { remoteSupportService.result(device, id, result); return AgentEnvelope.ok(new com.haodaone.software.dto.AgentAck(true, "Remote support result recorded")); }
}
