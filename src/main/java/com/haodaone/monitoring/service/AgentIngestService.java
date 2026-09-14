package com.haodaone.monitoring.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.*;
import com.haodaone.monitoring.entity.ActivitySession;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.ActivitySessionRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic behind POST /api/agent/heartbeat and POST
 * /api/agent/activity/batch. The caller (AgentController) has already been
 * authenticated by security.AgentTokenAuthenticationFilter, which resolves
 * the bearer token to a MonitoredDevice and passes it in here - this
 * service never re-validates the token itself, same separation
 * JwtAuthenticationFilter/CustomUserDetailsService already establish for
 * user auth.
 *
 * INCIDENT WRITEUP - "activity/batch returns 200 but activity_session never
 * gets new rows":
 *
 * Root cause #1 (the actual bug): JSON casing. See ActivityBatchRequest's
 * javadoc - the agent's real wire format is PascalCase, Jackson's default
 * matching is case-sensitive, and Spring Boot doesn't fail on unmatched
 * properties, so {@code request.getSessions()} silently deserialized to an
 * empty list. The for-loop below then had nothing to iterate, "accepted"
 * stayed empty, and the controller returned a perfectly well-formed 200
 * with acceptedSessionIds=[] - no exception anywhere in the stack, which is
 * exactly why this was invisible without the logging added below. Fixed by
 * @JsonAlias on every agent DTO field plus a global case-insensitive
 * Jackson customizer (config.JacksonConfig).
 *
 * Root cause #2 (a real but separate latent bug, fixed defensively even
 * though it isn't what produced the reported symptom): the MonitoredDevice
 * handed in as {@code authenticatedDevice} was loaded by
 * AgentTokenAuthenticationFilter via a plain repository call, which opens
 * and commits its own short-lived transaction and returns a DETACHED
 * entity. recordActivityBatch used that detached instance directly as the
 * FK target for every ActivitySession AND never re-saved it, so (a) any
 * identity resync done in syncDeviceIdentity (new employee assignment, IP,
 * agent version, etc.) during an activity-batch call was computed and then
 * silently discarded - only recordHeartbeat happened to persist it - and
 * (b) any code path that ends up touching a LAZY association on that
 * detached device (e.g. device.getEmployee()) is one Hibernate/driver
 * version away from a LazyInitializationException. Both methods below now
 * re-fetch a managed instance by id at the top of the transaction instead
 * of trusting the filter-supplied reference, and recordActivityBatch now
 * saves the device the same way recordHeartbeat always did.
 */
@Service
public class AgentIngestService {

    private static final Logger log = LoggerFactory.getLogger(AgentIngestService.class);

    private final MonitoredDeviceRepository deviceRepository;
    private final ActivitySessionRepository activitySessionRepository;
    private final EmployeeRepository employeeRepository;

    public AgentIngestService(MonitoredDeviceRepository deviceRepository,
                               ActivitySessionRepository activitySessionRepository,
                               EmployeeRepository employeeRepository) {
        this.deviceRepository = deviceRepository;
        this.activitySessionRepository = activitySessionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public HeartbeatResponseData recordHeartbeat(MonitoredDevice authenticatedDevice, HeartbeatRequest request, String remoteIp) {
        MonitoredDevice device = resolveDevice(authenticatedDevice, request.getDevice());
        device = syncDeviceIdentity(device, request.getDevice(), remoteIp);

        device.setStatus(request.getStatus() != null ? request.getStatus() : "ONLINE");
        device.setCurrentApplication(request.getCurrentApplication());
        device.setCurrentWindowTitle(request.getCurrentWindowTitle());
        device.setLastSeenAt(request.getLastSeenUtc() != null
                ? request.getLastSeenUtc().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
                : LocalDateTime.now());
        if (request.getAgentVersion() != null) {
            device.setAgentVersion(request.getAgentVersion());
        }

        deviceRepository.save(device);

        HeartbeatResponseData.Directive directive = null;
        if (device.getHeartbeatIntervalSeconds() != null || device.isMonitoringPaused()) {
            directive = new HeartbeatResponseData.Directive(device.getHeartbeatIntervalSeconds(), device.isMonitoringPaused());
        }
        return HeartbeatResponseData.accepted(directive);
    }

    @Transactional
    public ActivityBatchResponseData recordActivityBatch(MonitoredDevice authenticatedDevice, ActivityBatchRequest request, String remoteIp) {
        // --- Diagnostic logging (requested for this incident) ---------------
        // These two lines are the whole story: if "sessions" logs 0 while the
        // agent's own logs say N sessions were flushed, the request body did
        // not deserialize the way we think it did - see the class javadoc.
        log.info("Activity batch received: {} sessions",
                request.getSessions() == null ? 0 : request.getSessions().size());
        log.info("Device payload: {}",
                request.getDevice() == null ? "NULL" : request.getDevice().getDeviceId());

        // Re-fetch a MANAGED instance instead of trusting the detached one
        // the auth filter supplied - see class javadoc, root cause #2.
        MonitoredDevice device = resolveDevice(authenticatedDevice, request.getDevice());
        device = syncDeviceIdentity(device, request.getDevice(), remoteIp);
        // recordHeartbeat always persisted identity-sync changes; this path
        // never did. Save it here too so an activity-only agent (monitoring
        // active, heartbeat lagging) doesn't silently lose employee/IP resync.
        device = deviceRepository.save(device);

        List<ActivitySessionPayload> sessions = request.getSessions();
        if (sessions == null || sessions.isEmpty()) {
            log.warn("Activity batch for device {} contained no sessions after deserialization - " +
                            "if the agent's own logs say it sent sessions, this is a request-body " +
                            "binding mismatch (casing/shape), not a persistence failure.",
                    device.getDeviceId());
            return ActivityBatchResponseData.of(new ArrayList<>());
        }

        List<String> accepted = new ArrayList<>();
        Long companyId = device.getCompany() != null ? device.getCompany().getId() : null;
        if (companyId == null) {
            throw new IllegalStateException("Authenticated monitoring device has no company");
        }
        int savedCount = 0;
        for (ActivitySessionPayload payload : sessions) {
            if (activitySessionRepository.existsBySessionId(payload.getSessionId())) {
                // Already persisted from a previous delivery attempt - still report it accepted
                // so the agent's LocalCacheService prunes it, matching ApiClientService's contract.
                accepted.add(payload.getSessionId());
                continue;
            }

            // The authenticated monitored_device assignment is authoritative.
            // Windows usernames are telemetry, not HRMS identity.
            com.haodaone.employee.entity.Employee employee = device.getEmployee();

            log.info("Saving activity session {} (device={}, app={}, start={})",
                    payload.getSessionId(), device.getDeviceId(), payload.getApplicationName(), payload.getStartTimeUtc());
            int inserted = activitySessionRepository.insertIfAbsent(
                    payload.getSessionId(),
                    device.getId(),
                    employee != null ? employee.getId() : null,
                    companyId,
                    payload.getProcessName(),
                    payload.getApplicationName(),
                    payload.getWindowTitle(),
                    toLocalDateTime(payload.getStartTimeUtc()),
                    toLocalDateTime(payload.getEndTimeUtc()),
                    payload.getDurationSeconds(),
                    payload.isIdleSession());
            if (inserted > 0) {
                log.info("Saved activity session {}", payload.getSessionId());
                savedCount++;
                accepted.add(payload.getSessionId());
            } else if (activitySessionRepository.existsBySessionIdAndDevice_IdAndCompany_Id(
                    payload.getSessionId(), device.getId(), companyId)) {
                log.info("Accepted replayed activity session {} for device {}", payload.getSessionId(), device.getDeviceId());
                accepted.add(payload.getSessionId());
            } else {
                log.warn("Rejected activity session {} because its id is already owned by another device or tenant",
                        payload.getSessionId());
            }
        }

        log.info("Accepted {}/{} activity sessions for device {} ({} newly inserted, {} already present)",
                accepted.size(), sessions.size(), device.getDeviceId(), savedCount, accepted.size() - savedCount);
        return ActivityBatchResponseData.of(accepted);
    }

    /**
     * Re-fetches the device by id inside the CURRENT transaction/persistence
     * context. authenticatedDevice (from @AuthenticationPrincipal) was
     * loaded by AgentTokenAuthenticationFilter in its own already-committed
     * transaction, so it is detached here - using it directly is what let
     * identity-sync writes get silently dropped in recordActivityBatch, and
     * is one Hibernate upgrade away from a LazyInitializationException the
     * first time a lazy field on it (e.g. employee) is actually initialized
     * rather than just null-checked. This keeps every write in this service
     * operating on a managed entity for the rest of the transaction.
     */
    private MonitoredDevice attachManagedDevice(MonitoredDevice authenticatedDevice) {
        return deviceRepository.findById(authenticatedDevice.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated device id " + authenticatedDevice.getId() + " no longer exists"));
    }

    private MonitoredDevice resolveDevice(MonitoredDevice authenticatedDevice, DeviceInfoPayload payload) {
        MonitoredDevice authenticated = attachManagedDevice(authenticatedDevice);
        log.info("Agent identity: authenticatedDatabaseId={}, authenticatedDeviceId={}, payloadDeviceId={}, "
                + "payloadUsername={}, payloadDeviceName={}, companyId={}",
            authenticated.getId(),
            authenticated.getDeviceId(),
            payload != null ? payload.getDeviceId() : null,
            payload != null ? payload.getUsername() : null,
            payload != null ? payload.getDeviceName() : null,
            authenticated.getCompany() != null ? authenticated.getCompany().getId() : null);
        if (payload == null || payload.getDeviceId() == null || payload.getDeviceId().isBlank()
                || !authenticated.getDeviceId().startsWith("PENDING-")) {
            return authenticated;
        }

        java.util.Optional<MonitoredDevice> existing =
                deviceRepository.findByDeviceIdAndDeletedFalse(payload.getDeviceId());
        if (existing.isEmpty() || existing.get().getId().equals(authenticated.getId())) {
            return authenticated;
        }

        Long authenticatedCompanyId = authenticated.getCompany() != null ? authenticated.getCompany().getId() : null;
        Long existingCompanyId = existing.get().getCompany() != null ? existing.get().getCompany().getId() : null;
        if (authenticatedCompanyId == null || !authenticatedCompanyId.equals(existingCompanyId)) {
            throw new BadRequestException("Device ID is already registered to another company");
        }

        log.warn("Reconciling pending authenticated device {} to existing device {} for deviceId {}",
                authenticated.getId(), existing.get().getId(), payload.getDeviceId());
        return attachManagedDevice(existing.get());
    }

    /**
     * Fills in the agent-generated deviceId on first check-in (enrollment
     * only knows the admin-chosen name, not the hardware id - see
     * DeviceEnrollmentService#enroll) and refreshes identifying fields on
     * every call after that. Guards against a deviceId collision with a
     * different already-identified device, which would indicate a token
     * reused across two machines.
     */
    private MonitoredDevice syncDeviceIdentity(MonitoredDevice device, DeviceInfoPayload payload, String remoteIp) {
        if (payload != null && payload.getDeviceId() != null && !payload.getDeviceId().isBlank()) {
            if (device.getDeviceId().startsWith("PENDING-")) {
                java.util.Optional<MonitoredDevice> existing =
                        deviceRepository.findByDeviceIdAndDeletedFalse(payload.getDeviceId());
                if (existing.isPresent() && !existing.get().getId().equals(device.getId())) {
                    throw new BadRequestException(
                            "Device ID is already registered to another monitored device");
                }
                device.setDeviceId(payload.getDeviceId());
            } else if (!device.getDeviceId().equals(payload.getDeviceId())) {
                log.warn("Device {} reported deviceId {} which does not match enrolled deviceId {} - keeping enrolled value",
                        device.getId(), payload.getDeviceId(), device.getDeviceId());
            }
            if (payload.getDeviceName() != null && !payload.getDeviceName().isBlank()) {
                device.setDeviceName(payload.getDeviceName());
            }
            device.setWindowsUsername(payload.getUsername());
            device.setDomainName(payload.getDomainName());
            device.setOperatingSystem(payload.getOperatingSystem());
            device.setOsVersion(payload.getOsVersion());
            device.setMacAddress(payload.getMacAddress());
            if (payload.getHostname() != null && !payload.getHostname().isBlank()) {
                device.setHostname(payload.getHostname());
            }
            if (payload.getMachineGuid() != null && !payload.getMachineGuid().isBlank()) {
                device.setMachineGuid(payload.getMachineGuid());
            }
            if (payload.getAgentVersion() != null) {
                device.setAgentVersion(payload.getAgentVersion());
            }
            device.setIpAddress(payload.getIpAddress());

            // Do not resolve or overwrite employee from agent-controlled fields.
            // Assignment is owned by the authenticated monitored_device row and
            // changes only through the admin assignment API.
        }
        device.setLastIpAddress(remoteIp);
        return device;
    }

    private LocalDateTime toLocalDateTime(java.time.OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }
}
