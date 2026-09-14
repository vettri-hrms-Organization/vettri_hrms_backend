package com.haodaone.monitoring.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.dto.ActivitySessionDTO;
import com.haodaone.monitoring.repository.ActivitySessionRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MonitoringQueryService {

    private final ActivitySessionRepository activitySessionRepository;
    private final MonitoredDeviceRepository deviceRepository;

    public MonitoringQueryService(
            ActivitySessionRepository activitySessionRepository,
            MonitoredDeviceRepository deviceRepository) {

        this.activitySessionRepository = activitySessionRepository;
        this.deviceRepository = deviceRepository;
    }

    /**
     * Combined date + employee + device filter for the Activity page - the
     * root fix for "No activity in this range": previously byDevice(),
     * byEmployee() and byDateRange() below were mutually exclusive, so a
     * page that needs to filter by date range AND employee/device at the
     * same time had no working endpoint to call. Also accepts employeeCode
     * directly (e.g. "HAODA-0042") - the same identifier shown everywhere
     * else in the UI (Device Assignment table's "Employee ID" column) -
     * instead of forcing the caller to resolve it to Employee.id first.
     */
    public Page<ActivitySessionDTO> search(LocalDateTime from, LocalDateTime to, Long employeeId,
                                            String employeeCode, Long deviceId, String windowTitle, int page, int size) {
        if (from == null) {
            throw new IllegalArgumentException("From date must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("To date must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not be after To date");
        }

        Pageable pageable = createPageable(page, size);
        String trimmedCode = (employeeCode == null || employeeCode.isBlank()) ? null : employeeCode.trim();
        String windowTitlePattern = (windowTitle == null || windowTitle.isBlank())
                ? null
                : "%" + windowTitle.trim() + "%";
        Long companyId = requiredTenant();

        return activitySessionRepository
            .searchPaged(from, to, companyId, employeeId, trimmedCode, deviceId, windowTitlePattern, pageable)
                .map(ActivitySessionDTO::from);
    }

    /**
     * Get paginated activity sessions for a monitored device.
     */
    public Page<ActivitySessionDTO> byDevice(
            Long deviceId,
            int page,
            int size) {

        if (deviceId == null) {
            throw new IllegalArgumentException("Device ID must not be null");
        }

        Long companyId = requiredTenant();
        if (!deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId).isPresent()) {
            throw new ResourceNotFoundException(
                    "Monitored device not found: " + deviceId
            );
        }

        Pageable pageable = createPageable(page, size);

        return activitySessionRepository
                .findByDevice_IdAndCompany_IdOrderByStartTimeDesc(deviceId, companyId, pageable)
                .map(ActivitySessionDTO::from);
    }

    /**
     * Get paginated activity sessions for an employee.
     */
    public Page<ActivitySessionDTO> byEmployee(
            Long employeeId,
            int page,
            int size) {

        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID must not be null");
        }

        Pageable pageable = createPageable(page, size);
        Long companyId = requiredTenant();

        return activitySessionRepository
            .findByEmployee_IdAndCompany_IdOrderByStartTimeDesc(employeeId, companyId, pageable)
                .map(ActivitySessionDTO::from);
    }

    /**
     * Get paginated activity sessions within a date/time range.
     */
    public Page<ActivitySessionDTO> byDateRange(
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {

        if (from == null) {
            throw new IllegalArgumentException("From date must not be null");
        }

        if (to == null) {
            throw new IllegalArgumentException("To date must not be null");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "From date must not be after To date"
            );
        }

        Pageable pageable = createPageable(page, size);
        Long companyId = requiredTenant();

        return activitySessionRepository
                .findByStartTimeBetweenOrderByStartTimeDesc(
                        from,
                        to,
                        companyId,
                        pageable
                )
                .map(ActivitySessionDTO::from);
    }

    /**
     * Prevent invalid pagination parameters from reaching Spring Data.
     */
    private Pageable createPageable(int page, int size) {

        int safePage = Math.max(page, 0);

        int safeSize;
        if (size <= 0) {
            safeSize = 20;
        } else {
            safeSize = Math.min(size, 100);
        }

        return PageRequest.of(safePage, safeSize);
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }
}