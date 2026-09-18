package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.exception.AttendanceLocationException;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class AttendanceValidationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceValidationService.class);
    private static final long MAX_LOCATION_AGE_MILLIS = 120_000L;

    @Value("${attendance.location.good-accuracy-meters:100}")
    private double goodAccuracyMeters = 100.0;

    @Value("${attendance.location.max-accuracy-meters:200}")
    private double maxAccuracyMeters = 200.0;

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final MonitoredDeviceRepository monitoredDeviceRepository;
    private final WfhRequestRepository wfhRequestRepository;

    public AttendanceValidationService(AttendanceSessionRepository attendanceSessionRepository,
                                      OfficeLocationRepository officeLocationRepository,
                                      MonitoredDeviceRepository monitoredDeviceRepository,
                                      WfhRequestRepository wfhRequestRepository) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.monitoredDeviceRepository = monitoredDeviceRepository;
        this.wfhRequestRepository = wfhRequestRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceSession currentActiveSession(Employee employee, Company company) {
        return attendanceSessionRepository
                .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                        employee.getId(), company.getId(), LocalDate.now(), List.of("CHECKED_IN"))
                .orElse(null);
    }

    public OfficeLocation resolveOffice(Company company, Long officeLocationId) {
        if (officeLocationId == null) {
            List<OfficeLocation> offices = officeLocationRepository.findAllByCompany_IdAndActiveTrueAndDeletedFalseOrderByNameAsc(company.getId());
            if (offices.isEmpty()) throw new BadRequestException("NO_ACTIVE_OFFICE");
            return offices.get(0);
        }
        return officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(officeLocationId, company.getId())
                .filter(OfficeLocation::isActive)
                .orElseThrow(() -> new BadRequestException("NO_ACTIVE_OFFICE"));
    }

    public void validateLocation(Double latitude, Double longitude, Double accuracy, Long timestamp,
                                 OfficeLocation officeLocation, String source) {
        double distanceMeters = 0.0;
        double effectiveRequiredAccuracyMeters = maxAccuracyMeters;

        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw locationError("LOCATION_UNAVAILABLE", "Location coordinates are unavailable.", accuracy, null, null, null, "COORDINATES", source);
        }
        if (timestamp == null || Math.abs(System.currentTimeMillis() - timestamp) > MAX_LOCATION_AGE_MILLIS) {
            throw locationError("LOCATION_STALE", "The location fix is too old to verify this check-in.", accuracy, null, null, null, "FRESHNESS", source);
        }
        if (accuracy == null || !Double.isFinite(accuracy) || accuracy < 0) {
            throw locationError("LOCATION_UNAVAILABLE", "Location accuracy is unavailable.", accuracy, effectiveRequiredAccuracyMeters, null, null, "ACCURACY", source);
        }
        if (accuracy > maxAccuracyMeters) {
            throw locationError("LOCATION_INACCURATE", "Your device could not determine your location accurately enough to check in.", accuracy, effectiveRequiredAccuracyMeters, null, null, "ACCURACY", source);
        }
        if (officeLocation == null) {
            throw new BadRequestException("NO_ACTIVE_OFFICE");
        }
        distanceMeters = calculateDistance(latitude, longitude, officeLocation.getLatitude(), officeLocation.getLongitude());
        String accuracyBand = accuracy <= goodAccuracyMeters ? "GOOD" : "MODERATE";
        log.info("CHECK-IN LOCATION VALIDATION accuracyBand={} receivedLatitude={} receivedLongitude={} receivedAccuracyMeters={} goodAccuracyMeters={} maxAccuracyMeters={} officeLatitude={} officeLongitude={} allowedRadiusMeters={} distanceMeters={} source={}",
                accuracyBand, latitude, longitude, accuracy, goodAccuracyMeters, maxAccuracyMeters, officeLocation.getLatitude(), officeLocation.getLongitude(), officeLocation.getAllowedRadiusMeters(), distanceMeters, source);
        if (distanceMeters > officeLocation.getAllowedRadiusMeters()) {
            throw locationError("OUTSIDE_GEOFENCE", "You are outside the allowed office check-in area.", accuracy,
                    effectiveRequiredAccuracyMeters, distanceMeters, officeLocation.getAllowedRadiusMeters(), "GEOFENCE", source);
        }
    }

    private AttendanceLocationException locationError(String code, String message, Double accuracy,
                                                       Double requiredAccuracy, Double distance,
                                                       Integer radius, String stage, String source) {
        return new AttendanceLocationException(code, message, accuracy, requiredAccuracy, distance, radius, stage, source);
    }

    public String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "WEB_DESKTOP";
        return switch (source.trim().toUpperCase()) {
            case "MANAGED_DEVICE", "MANAGEDDEVICE", "DEVICE" -> "MANAGED_DEVICE";
            case "WEB_MOBILE", "MOBILE" -> "WEB_MOBILE";
            case "WEB_DESKTOP", "WEB" -> "WEB_DESKTOP";
            default -> "WEB_DESKTOP";
        };
    }

    public void validateManagedDevice(Employee employee, String deviceId, String source) {
        if (!"MANAGED_DEVICE".equalsIgnoreCase(source) || deviceId == null || deviceId.isBlank()) {
            return;
        }
        MonitoredDevice device = monitoredDeviceRepository.findByDeviceIdAndDeletedFalse(deviceId)
                .orElseThrow(() -> new BadRequestException("DEVICE_NOT_AUTHORIZED"));
        if (device.getCompany() == null || employee.getCompany() == null || !Objects.equals(device.getCompany().getId(), employee.getCompany().getId())) {
            throw new BadRequestException("DEVICE_NOT_AUTHORIZED");
        }
        if (!device.isActive()) {
            throw new BadRequestException("DEVICE_NOT_AUTHORIZED");
        }
        if (device.getEmployee() != null && !Objects.equals(device.getEmployee().getId(), employee.getId())) {
            throw new BadRequestException("DEVICE_NOT_AUTHORIZED");
        }
    }

    public void validateWfhApproval(Employee employee, Company company, LocalDate date) {
        WfhRequest request = wfhRequestRepository.findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(
                employee.getId(), company.getId(), date).orElse(null);
        if (request == null || !"APPROVED".equalsIgnoreCase(request.getStatus())) {
            throw new BadRequestException("WFH_APPROVAL_REQUIRED");
        }
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusMeters = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}
