package com.haodaone.remotedesktop.dto;

import com.haodaone.remotedesktop.entity.*;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public final class RemoteDesktopDTO {
    private RemoteDesktopDTO() { }
    public record Session(Long sessionId, Long deviceId, String deviceName, String status, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime endedAt, String failureReason, String signalingToken, List<IceServer> iceServers, Instant signalingExpiresAt) {
        public static Session from(RemoteDesktopSession s) { return new Session(s.getId(), s.getDevice().getId(), s.getDevice().getDeviceName(), s.getStatus().name(), s.getCreatedAt(), s.getStartedAt(), s.getEndedAt(), s.getFailureReason(), null, List.of(), null); }
        public static Session withWebRtc(RemoteDesktopSession s, String token, List<IceServer> servers, Instant expiresAt) { return new Session(s.getId(), s.getDevice().getId(), s.getDevice().getDeviceName(), s.getStatus().name(), s.getCreatedAt(), s.getStartedAt(), s.getEndedAt(), s.getFailureReason(), token, servers, expiresAt); }
    }
    public record IceServer(String urls, String username, String credential) { }
    public record WebRtcSignal(String sessionId, String type, String sdp, String candidate, String sdpMid, Integer sdpMLineIndex, String status, String reason) { }
    public record WebRtcEvent(String sessionId, String type, String sdp, String candidate, String sdpMid, Integer sdpMLineIndex, String status, String reason, List<IceServer> iceServers) {
        public WebRtcEvent(String sessionId, String type, String sdp, String candidate, String sdpMid, Integer sdpMLineIndex, String status, String reason) {
            this(sessionId, type, sdp, candidate, sdpMid, sdpMLineIndex, status, reason, List.of());
        }
        public WebRtcEvent withIceServers(List<IceServer> servers) {
            return new WebRtcEvent(sessionId, type, sdp, candidate, sdpMid, sdpMLineIndex, status, reason, servers);
        }
    }
    public record AgentSession(String sessionId, String action) { }
    public record AgentFrame(String sessionId, String imageBase64, Integer screenWidth, Integer screenHeight) { }
    public record InputEvent(String type, Integer x, Integer y, String button, Integer delta, Integer virtualKey, Boolean keyDown) { }
    public record AgentInput(String sessionId, String type, Integer x, Integer y, String button, Integer delta, Integer virtualKey, Boolean keyDown) { }
}