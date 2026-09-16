package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class AttendanceValidationService {

    private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 50.0;

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

    public void validateLocation(Double latitude, Double longitude, Double accuracy, OfficeLocation officeLocation) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException("LOCATION_UNAVAILABLE");
        }
        if (accuracy == null || accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            throw new BadRequestException("LOCATION_INACCURATE");
        }
        if (officeLocation == null) {
            throw new BadRequestException("NO_ACTIVE_OFFICE");
        }
        double distance = calculateDistance(latitude, longitude, officeLocation.getLatitude(), officeLocation.getLongitude());
        if (distance > officeLocation.getAllowedRadiusMeters()) {
            throw new BadRequestException("OUTSIDE_GEOFENCE");
        }
    }

    public String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "WEB";
        return switch (source.trim().toUpperCase()) {
            case "MANAGED_DEVICE", "MANAGEDDEVICE", "DEVICE" -> "MANAGED_DEVICE";
            case "MOBILE" -> "MOBILE";
            case "WEB" -> "WEB";
            default -> "WEB";
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
