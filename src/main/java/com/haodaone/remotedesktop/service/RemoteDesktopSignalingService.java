package com.haodaone.remotedesktop.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.remotedesktop.dto.RemoteDesktopDTO;
import com.haodaone.remotedesktop.entity.RemoteDesktopSession;
import com.haodaone.remotedesktop.entity.RemoteDesktopStatus;
import com.haodaone.remotedesktop.repository.RemoteDesktopSessionRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RemoteDesktopSignalingService {
    private static final long AUTHORIZATION_SECONDS = 300;
    private static final int MAX_SDP_LENGTH = 100_000;
    private static final int MAX_CANDIDATE_LENGTH = 10_000;

    private final RemoteDesktopSessionRepository sessionRepository;
    private final AuditLogService auditLogService;
    private final String turnUrls;
    private final String turnUsername;
    private final String turnPassword;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public RemoteDesktopSignalingService(RemoteDesktopSessionRepository sessionRepository,
                                         AuditLogService auditLogService,
                                         @Value("${app.webrtc.turn.urls:}") String turnUrls,
                                         @Value("${app.webrtc.turn.username:}") String turnUsername,
                                         @Value("${app.webrtc.turn.password:}") String turnPassword) {
        this.sessionRepository = sessionRepository;
        this.auditLogService = auditLogService;
        this.turnUrls = turnUrls;
        this.turnUsername = turnUsername;
        this.turnPassword = turnPassword;
    }

    public RemoteDesktopDTO.Session initialize(RemoteDesktopSession session) {
        purgeExpired();
        String token = randomToken();
        Instant expiresAt = Instant.now().plusSeconds(AUTHORIZATION_SECONDS);
        states.put(session.getId(), new State(session.getCompany().getId(), session.getDevice().getId(), hash(token), expiresAt));
        return RemoteDesktopDTO.Session.withWebRtc(session, token, iceServers(), expiresAt);
    }

    public void browserOffer(Long deviceId, Long sessionId, String token, RemoteDesktopDTO.WebRtcSignal signal) {
        State state = browserState(deviceId, sessionId, token);
        requireType(signal, "OFFER");
        validateSdp(signal.sdp());
        state.toAgent.add(new RemoteDesktopDTO.WebRtcEvent(String.valueOf(sessionId), "OFFER", signal.sdp(), null, null, null, null, null));
        auditLogService.log("RemoteDesktopSession", sessionId, "WEBRTC_OFFER", "WebRTC offer queued for Agent");
    }

    public void browserIce(Long deviceId, Long sessionId, String token, RemoteDesktopDTO.WebRtcSignal signal) {
        State state = browserState(deviceId, sessionId, token);
        requireType(signal, "ICE");
        validateCandidate(signal.candidate());
        state.toAgent.add(new RemoteDesktopDTO.WebRtcEvent(String.valueOf(sessionId), "ICE", null, signal.candidate(), signal.sdpMid(), signal.sdpMLineIndex(), null, null));
    }

    public List<RemoteDesktopDTO.WebRtcEvent> browserEvents(Long deviceId, Long sessionId, String token) {
        State state = browserState(deviceId, sessionId, token);
        return drain(state.toBrowser);
    }

    public List<RemoteDesktopDTO.WebRtcEvent> agentEvents(MonitoredDevice device) {
        purgeExpired();
        if (device == null || device.getId() == null || device.getCompany() == null) return List.of();
        List<RemoteDesktopDTO.WebRtcEvent> result = new ArrayList<>();
        states.forEach((sessionId, state) -> {
            if (Objects.equals(state.deviceId, device.getId()) && Objects.equals(state.companyId, device.getCompany().getId())) {
                result.addAll(drain(state.toAgent).stream().map(event -> event.withIceServers(iceServers())).toList());
            }
        });
        return result;
    }

    @Transactional
    public void agentAnswer(MonitoredDevice device, RemoteDesktopDTO.WebRtcSignal signal) {
        State state = authorizedAgentState(device, signal);
        requireType(signal, "ANSWER");
        validateSdp(signal.sdp());
        state.toBrowser.add(new RemoteDesktopDTO.WebRtcEvent(signal.sessionId(), "ANSWER", signal.sdp(), null, null, null, null, null));
        auditLogService.log("RemoteDesktopSession", sessionId(signal), "WEBRTC_ANSWER", "WebRTC answer received from Agent");
    }

    public void agentIce(MonitoredDevice device, RemoteDesktopDTO.WebRtcSignal signal) {
        State state = authorizedAgentState(device, signal);
        requireType(signal, "ICE");
        validateCandidate(signal.candidate());
        state.toBrowser.add(new RemoteDesktopDTO.WebRtcEvent(signal.sessionId(), "ICE", null, signal.candidate(), signal.sdpMid(), signal.sdpMLineIndex(), null, null));
    }

    @Transactional
    public void agentState(MonitoredDevice device, RemoteDesktopDTO.WebRtcSignal signal) {
        State state = authorizedAgentState(device, signal);
        String status = signal.status() == null ? "" : signal.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CONNECTED", "FAILED", "DISCONNECTED").contains(status)) throw new BadRequestException("Unsupported WebRTC state");
        RemoteDesktopSession session = session(sessionId(signal), state);
        if (status.equals("CONNECTED")) {
            session.setStatus(RemoteDesktopStatus.CONNECTED);
            session.setStartedAt(session.getStartedAt() == null ? LocalDateTime.now() : session.getStartedAt());
        } else if (status.equals("FAILED")) {
            session.setStatus(RemoteDesktopStatus.FAILED);
            session.setFailureReason(trimReason(signal.reason()));
            session.setEndedAt(LocalDateTime.now());
        } else if (session.getStatus() != RemoteDesktopStatus.CANCELLED) {
            session.setStatus(RemoteDesktopStatus.DISCONNECTED);
            session.setEndedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);
        auditLogService.log("RemoteDesktopSession", session.getId(), "WEBRTC_" + status, "WebRTC Agent state=" + status);
        state.toBrowser.add(new RemoteDesktopDTO.WebRtcEvent(signal.sessionId(), "STATE", null, null, null, null, status, signal.reason()));
    }

    public void close(Long sessionId, String reason) {
        State state = states.remove(sessionId);
        if (state != null) {
            state.toAgent.add(new RemoteDesktopDTO.WebRtcEvent(String.valueOf(sessionId), "END", null, null, null, null, null, reason));
            auditLogService.log("RemoteDesktopSession", sessionId, "WEBRTC_DISCONNECT", "WebRTC signaling closed: " + trimReason(reason));
        }
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireSessions() {
        Instant now = Instant.now();
        states.forEach((sessionId, state) -> {
            if (now.isAfter(state.expiresAt) && states.remove(sessionId, state)) {
                RemoteDesktopSession session = sessionRepository.findById(sessionId).orElse(null);
                if (session != null && state.companyId.equals(session.getCompany().getId()) &&
                        (session.getStatus() == RemoteDesktopStatus.REQUESTED || session.getStatus() == RemoteDesktopStatus.CONNECTING)) {
                    session.setStatus(RemoteDesktopStatus.EXPIRED);
                    session.setFailureReason("WebRTC signaling authorization expired");
                    session.setEndedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                    TenantContext.setCurrentTenant(state.companyId);
                    try { auditLogService.log("RemoteDesktopSession", sessionId, "WEBRTC_EXPIRED", "WebRTC signaling authorization expired"); }
                    finally { TenantContext.clear(); }
                }
            }
        });
    }

    private State browserState(Long deviceId, Long sessionId, String token) {
        purgeExpired();
        State state = states.get(sessionId);
        if (state == null || !Objects.equals(state.deviceId, deviceId) || !constantTimeEquals(state.tokenHash, hash(token))) throw new ResourceNotFoundException("WebRTC session authorization not found");
        session(sessionId, state);
        return state;
    }

    private State authorizedAgentState(MonitoredDevice device, RemoteDesktopDTO.WebRtcSignal signal) {
        long sessionId = sessionId(signal);
        State state = states.get(sessionId);
        if (state == null || device == null || !Objects.equals(state.deviceId, device.getId()) || device.getCompany() == null || !Objects.equals(state.companyId, device.getCompany().getId())) throw new ResourceNotFoundException("WebRTC session not found");
        session(sessionId, state);
        return state;
    }

    private RemoteDesktopSession session(long sessionId, State state) {
        return sessionRepository.findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(sessionId, state.companyId, state.deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Remote desktop session not found: " + sessionId));
    }

    private List<RemoteDesktopDTO.WebRtcEvent> drain(Queue<RemoteDesktopDTO.WebRtcEvent> queue) {
        List<RemoteDesktopDTO.WebRtcEvent> result = new ArrayList<>();
        RemoteDesktopDTO.WebRtcEvent event;
        while (result.size() < 100 && (event = queue.poll()) != null) result.add(event);
        return result;
    }

    private List<RemoteDesktopDTO.IceServer> iceServers() {
        if (turnUrls == null || turnUrls.isBlank()) return List.of();
        return Arrays.stream(turnUrls.split(","))
                .map(String::trim).filter(url -> !url.isEmpty())
                .map(url -> new RemoteDesktopDTO.IceServer(url, turnUsername, turnPassword)).toList();
    }

    private void requireType(RemoteDesktopDTO.WebRtcSignal signal, String expected) {
        if (signal == null || !expected.equalsIgnoreCase(signal.type())) throw new BadRequestException("Expected WebRTC " + expected.toLowerCase(Locale.ROOT) + " message");
    }

    private void validateSdp(String sdp) {
        if (sdp == null || sdp.isBlank() || sdp.length() > MAX_SDP_LENGTH) throw new BadRequestException("Invalid WebRTC SDP");
    }

    private void validateCandidate(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_CANDIDATE_LENGTH) throw new BadRequestException("Invalid WebRTC ICE candidate");
    }

    private long sessionId(RemoteDesktopDTO.WebRtcSignal signal) {
        try { return Long.parseLong(signal.sessionId()); } catch (Exception ex) { throw new BadRequestException("Invalid remote desktop session"); }
    }

    private String trimReason(String reason) { return reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)); }
    private void purgeExpired() { expireSessions(); }
    private String randomToken() { byte[] value = new byte[32]; secureRandom.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String hash(String value) { if (value == null) return ""; try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); } }
    private boolean constantTimeEquals(String left, String right) { return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)); }

    private static final class State {
        private final Long companyId;
        private final Long deviceId;
        private final String tokenHash;
        private final Instant expiresAt;
        private final Queue<RemoteDesktopDTO.WebRtcEvent> toAgent = new ConcurrentLinkedQueue<>();
        private final Queue<RemoteDesktopDTO.WebRtcEvent> toBrowser = new ConcurrentLinkedQueue<>();
        private State(Long companyId, Long deviceId, String tokenHash, Instant expiresAt) { this.companyId = companyId; this.deviceId = deviceId; this.tokenHash = tokenHash; this.expiresAt = expiresAt; }
    }
}