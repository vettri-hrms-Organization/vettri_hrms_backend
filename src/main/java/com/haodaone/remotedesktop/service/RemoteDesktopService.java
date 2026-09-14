package com.haodaone.remotedesktop.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.remotedesktop.dto.RemoteDesktopDTO;
import com.haodaone.remotedesktop.entity.*;
import com.haodaone.remotedesktop.repository.RemoteDesktopSessionRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RemoteDesktopService {
    private static final Logger log = LoggerFactory.getLogger(RemoteDesktopService.class);
    private static final List<RemoteDesktopStatus> ACTIVE = List.of(RemoteDesktopStatus.REQUESTED, RemoteDesktopStatus.CONNECTING, RemoteDesktopStatus.CONNECTED);
    private static final List<RemoteDesktopStatus> AGENT_VISIBLE = List.of(RemoteDesktopStatus.REQUESTED, RemoteDesktopStatus.CONNECTING, RemoteDesktopStatus.CONNECTED, RemoteDesktopStatus.CANCELLED);
    private final RemoteDesktopSessionRepository sessionRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final RemoteDesktopSignalingService signalingService;
    private final Map<Long, Frame> frames = new ConcurrentHashMap<>();
    private final Map<Long, Queue<RemoteDesktopDTO.AgentInput>> inputQueues = new ConcurrentHashMap<>();

    public RemoteDesktopService(RemoteDesktopSessionRepository sessionRepository, MonitoredDeviceRepository deviceRepository, AuditLogService auditLogService, RemoteDesktopSignalingService signalingService) {
        this.sessionRepository = sessionRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.signalingService = signalingService;
    }

    @Transactional
    public RemoteDesktopDTO.Session start(Long deviceId, Long requestedBy) {
        Long companyId = tenant();
        MonitoredDevice device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + deviceId));
        if (!device.isOnline()) throw new BadRequestException("The selected device is currently offline");
        if (!sessionRepository.findByDevice_IdAndStatusInAndDeletedFalse(deviceId, ACTIVE).isEmpty()) throw new BadRequestException("A remote desktop session is already active for this device");
        RemoteDesktopSession session = new RemoteDesktopSession();
        session.setCompany(device.getCompany()); session.setDevice(device); session.setRequestedBy(requestedBy); session.setStatus(RemoteDesktopStatus.REQUESTED);
        RemoteDesktopSession saved = sessionRepository.save(session);
        log.info("Remote desktop stage=SESSION_CREATED sessionId={} deviceId={} deviceName={} companyId={} requestedBy={} status={}", saved.getId(), device.getId(), device.getDeviceName(), companyId, requestedBy, saved.getStatus());
        auditLogService.log("RemoteDesktopSession", saved.getId(), "CREATE", "Remote desktop requested for device '" + device.getDeviceName() + "'");
        return signalingService.initialize(saved);
    }

    @Transactional(readOnly = true)
    public RemoteDesktopDTO.Session get(Long deviceId, Long sessionId) {
        return RemoteDesktopDTO.Session.from(find(sessionId, tenant(), deviceId));
    }

    @Transactional(readOnly = true)
    public List<RemoteDesktopDTO.Session> list(Long deviceId) {
        Long companyId = tenant();
        deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + deviceId));
        return sessionRepository.findByCompany_IdAndDevice_IdAndDeletedFalseOrderByCreatedAtDesc(companyId, deviceId)
                .stream().map(RemoteDesktopDTO.Session::from).toList();
    }

    @Transactional
    public RemoteDesktopDTO.Session end(Long deviceId, Long sessionId) {
        RemoteDesktopSession session = find(sessionId, tenant(), deviceId);
        if (ACTIVE.contains(session.getStatus())) { session.setStatus(RemoteDesktopStatus.CANCELLED); session.setEndedAt(LocalDateTime.now()); sessionRepository.save(session); frames.remove(sessionId); auditLogService.log("RemoteDesktopSession", sessionId, "END", "Remote desktop session ended"); }
        signalingService.close(sessionId, "Browser ended session");
        inputQueues.remove(sessionId);
        return RemoteDesktopDTO.Session.from(session);
    }

    @Transactional
    public void enqueueInput(Long deviceId, Long sessionId, RemoteDesktopDTO.InputEvent input) {
        RemoteDesktopSession session = find(sessionId, tenant(), deviceId);
        if (!ACTIVE.contains(session.getStatus()) || input == null || input.type() == null) throw new BadRequestException("Remote desktop session is not active");
        String type = input.type().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MOVE", "BUTTON_DOWN", "BUTTON_UP", "WHEEL", "KEY_DOWN", "KEY_UP").contains(type)) throw new BadRequestException("Unsupported remote input event");
        if ((type.equals("MOVE") || type.startsWith("BUTTON")) && (input.x() == null || input.y() == null || input.x() < 0 || input.y() < 0)) throw new BadRequestException("Mouse coordinates are required");
        if (type.equals("WHEEL") && (input.delta() == null || input.delta() == 0)) throw new BadRequestException("Wheel delta is required");
        if ((type.equals("KEY_DOWN") || type.equals("KEY_UP")) && (input.virtualKey() == null || input.virtualKey() < 1 || input.virtualKey() > 255)) throw new BadRequestException("Virtual key is required");
        Queue<RemoteDesktopDTO.AgentInput> queue = inputQueues.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
        if (type.equals("MOVE")) {
            queue.removeIf(event -> event.type().equals("MOVE"));
        } else if (queue.size() >= 200) {
            throw new BadRequestException("Remote input queue is busy");
        }
        queue.add(new RemoteDesktopDTO.AgentInput(String.valueOf(sessionId), type, input.x(), input.y(), input.button(), input.delta(), input.virtualKey(), input.keyDown()));
        log.info("Remote desktop input stage=QUEUED sessionId={} deviceId={} type={} virtualKey={} queueSize={}", sessionId, deviceId, type, input.virtualKey(), queue.size());
    }

    @Transactional(readOnly = true)
    public List<RemoteDesktopDTO.AgentInput> agentInputs(MonitoredDevice device) {
        if (device == null) return List.of();
        List<RemoteDesktopDTO.AgentInput> result = new ArrayList<>();
        for (RemoteDesktopSession session : sessionRepository.findByDevice_IdAndStatusInAndDeletedFalse(device.getId(), List.of(RemoteDesktopStatus.CONNECTING, RemoteDesktopStatus.CONNECTED))) {
            Queue<RemoteDesktopDTO.AgentInput> queue = inputQueues.get(session.getId());
            if (queue == null) continue;
            RemoteDesktopDTO.AgentInput event;
            while ((event = queue.poll()) != null && result.size() < 50) result.add(event);
        }
        if (!result.isEmpty()) log.info("Remote desktop input stage=AGENT_DELIVERY deviceId={} eventCount={}", device.getId(), result.size());
        return result;
    }

    @Transactional
    public List<RemoteDesktopDTO.AgentSession> agentRequests(MonitoredDevice device) {
        if (device == null || device.getId() == null) return List.of();
        return sessionRepository.findByDevice_IdAndStatusInAndDeletedFalse(device.getId(), AGENT_VISIBLE).stream()
                .map(s -> {
                    if (s.getStatus() == RemoteDesktopStatus.REQUESTED) {
                        s.setStatus(RemoteDesktopStatus.CONNECTING);
                        s.setStartedAt(LocalDateTime.now());
                        sessionRepository.save(s);
                        log.info("Remote desktop stage=AGENT_REQUEST_DELIVERED sessionId={} deviceId={} deviceName={} status=CONNECTING", s.getId(), device.getId(), device.getDeviceName());
                    } else {
                        log.info("Remote desktop stage=AGENT_POLL sessionId={} deviceId={} action={} status={}", s.getId(), device.getId(), s.getStatus() == RemoteDesktopStatus.CANCELLED ? "STOP" : "START", s.getStatus());
                    }
                    return new RemoteDesktopDTO.AgentSession(String.valueOf(s.getId()), s.getStatus() == RemoteDesktopStatus.CANCELLED ? "STOP" : "START");
                }).toList();
    }

    @Transactional
    public void receiveFrame(MonitoredDevice device, RemoteDesktopDTO.AgentFrame payload) {
        long id;
        try { id = Long.parseLong(payload.sessionId()); } catch (Exception ex) { throw new BadRequestException("Invalid remote desktop session"); }
        RemoteDesktopSession session = sessionRepository.findByIdAndDevice_IdAndDeletedFalse(id, device.getId()).orElseThrow(() -> new ResourceNotFoundException("Remote desktop session not found"));
        if (!Objects.equals(session.getCompany().getId(), device.getCompany().getId()) || !ACTIVE.contains(session.getStatus())) throw new ResourceNotFoundException("Remote desktop session not found");
        if (payload.imageBase64() == null || payload.imageBase64().length() > 2_000_000) throw new BadRequestException("Screen frame is too large");
        byte[] image;
        try { image = Base64.getDecoder().decode(payload.imageBase64()); } catch (IllegalArgumentException ex) { throw new BadRequestException("Invalid screen frame"); }
        if (image.length == 0 || image.length > 1_500_000) throw new BadRequestException("Screen frame is too large");
        if (session.getStatus() != RemoteDesktopStatus.CONNECTED) { session.setStatus(RemoteDesktopStatus.CONNECTED); session.setStartedAt(session.getStartedAt() == null ? LocalDateTime.now() : session.getStartedAt()); sessionRepository.save(session); log.info("Remote desktop stage=FIRST_FRAME_RECEIVED sessionId={} deviceId={} frameBytes={} status=CONNECTED", session.getId(), device.getId(), image.length); }
        int screenWidth = payload.screenWidth() == null || payload.screenWidth() < 1 ? 1280 : payload.screenWidth();
        int screenHeight = payload.screenHeight() == null || payload.screenHeight() < 1 ? 720 : payload.screenHeight();
        frames.put(id, new Frame(image, screenWidth, screenHeight, System.currentTimeMillis()));
        log.debug("Remote desktop stage=FRAME_STORED sessionId={} deviceId={} frameBytes={}", session.getId(), device.getId(), image.length);
    }

    @Transactional(readOnly = true)
    public byte[] latestFrame(Long deviceId, Long sessionId) {
        find(sessionId, tenant(), deviceId);
        Frame frame = frames.get(sessionId);
        if (frame == null || System.currentTimeMillis() - frame.createdAt() > 10_000) throw new ResourceNotFoundException("No live screen frame available");
        log.debug("Remote desktop stage=FRAME_SERVED sessionId={} deviceId={} frameBytes={}", sessionId, deviceId, frame.bytes().length);
        return frame.bytes();
    }

    public Frame latestFrameInfo(Long deviceId, Long sessionId) {
        find(sessionId, tenant(), deviceId);
        Frame frame = frames.get(sessionId);
        if (frame == null || System.currentTimeMillis() - frame.createdAt() > 10_000) throw new ResourceNotFoundException("No live screen frame available");
        return frame;
    }

    public MediaType frameType() { return MediaType.IMAGE_JPEG; }
    private RemoteDesktopSession find(Long sessionId, Long companyId, Long deviceId) { return sessionRepository.findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(sessionId, companyId, deviceId).orElseThrow(() -> new ResourceNotFoundException("Remote desktop session not found: " + sessionId)); }
    private Long tenant() { Long id = TenantContext.getCurrentTenant(); if (id == null) throw new BadRequestException("Company context is required"); return id; }
    public record Frame(byte[] bytes, int width, int height, long createdAt) { }
}