package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AttendanceRecordDTO;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.entity.Device;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Speaks the eSSL/ZKTeco ADMS push protocol - same mechanics validated in
 * the standalone attendance POC (GET /iclock/cdata handshake, POST
 * /iclock/cdata?table=ATTLOG for punches, GET /iclock/getrequest polling),
 * now resolving punches against real Employee records instead of a flat
 * mapping table.
 */
@Service
public class AttendanceIngestService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceIngestService.class);
    private static final DateTimeFormatter DEVICE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> STATUS_TO_PUNCH_TYPE = new HashMap<>();
    static {
        STATUS_TO_PUNCH_TYPE.put("0", "IN");
        STATUS_TO_PUNCH_TYPE.put("1", "OUT");
        STATUS_TO_PUNCH_TYPE.put("4", "IN");
        STATUS_TO_PUNCH_TYPE.put("5", "OUT");
    }

    private static final Map<String, String> VERIFY_MODE_LABELS = new HashMap<>();
    static {
        VERIFY_MODE_LABELS.put("0", "Password");
        VERIFY_MODE_LABELS.put("1", "Fingerprint");
        VERIFY_MODE_LABELS.put("2", "Card");
        VERIFY_MODE_LABELS.put("15", "Face");
    }

    private final DeviceRepository deviceRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEventPublisher eventPublisher;

    public AttendanceIngestService(DeviceRepository deviceRepository, EmployeeRepository employeeRepository,
                                    AttendanceRecordRepository attendanceRecordRepository,
                                    AttendanceEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String handleHandshake(String serialNumber, String pushVersion, String remoteIp) {
        Device device = findOrRegisterDevice(serialNumber, remoteIp);
        if (pushVersion != null && !pushVersion.isBlank()) {
            device.setPushVersion(pushVersion);
            deviceRepository.save(device);
        }
        log.info("ADMS handshake from device SN={} ip={}", serialNumber, remoteIp);

        return "GET OPTION FROM: " + serialNumber + "\r\n"
                + "ATTLOGStamp=None\r\nOPERLOGStamp=None\r\nErrorDelay=30\r\n"
                + "Delay=10\r\nTransFlag=1111000000\r\nRealtime=1\r\nEncrypt=None\r\n";
    }

    public String handleGetRequest(String serialNumber) {
        return "OK";
    }

    @Transactional
    public int handleAttendanceLogs(String serialNumber, String body, String remoteIp) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        Device device = findOrRegisterDevice(serialNumber, remoteIp);

        String[] lines = body.split("\r\n|\n|\r");
        int saved = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                AttendanceRecord record = parseAndSaveLine(line.trim(), device);
                if (record != null) {
                    saved++;
                    eventPublisher.publish(AttendanceRecordDTO.from(record));
                }
            } catch (Exception ex) {
                log.error("Failed to parse ADMS line from SN={}: '{}' - {}", serialNumber, line, ex.getMessage());
            }
        }
        log.info("ADMS push from SN={}: {} line(s), {} saved", serialNumber, lines.length, saved);
        return saved;
    }

    private Device findOrRegisterDevice(String serialNumber, String remoteIp) {
        Device device = deviceRepository.findBySerialNumber(serialNumber).orElseGet(() -> {
            Device d = new Device();
            d.setSerialNumber(serialNumber);
            d.setDeviceName(serialNumber);
            return d;
        });
        device.setLastSeenAt(LocalDateTime.now());
        device.setLastIpAddress(remoteIp);
        return deviceRepository.save(device);
    }

    private AttendanceRecord parseAndSaveLine(String line, Device device) {
        String[] fields = line.split("\t");
        if (fields.length < 2) {
            return null;
        }
        String devicePin = fields[0].trim();
        LocalDateTime punchTime = LocalDateTime.parse(fields[1].trim(), DEVICE_TIME_FORMAT);
        String statusCode = fields.length > 2 ? fields[2].trim() : null;
        String verifyCode = fields.length > 3 ? fields[3].trim() : null;

        Optional<AttendanceRecord> existing = attendanceRecordRepository
                .findByDeviceSerialNumberAndDeviceUserIdAndPunchTime(device.getSerialNumber(), devicePin, punchTime);
        if (existing.isPresent()) {
            return null;
        }

        Employee employee = employeeRepository.findByBiometricDeviceUserIdAndDeletedFalse(devicePin).orElse(null);

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setEmployeeName(employee != null ? employee.getFullName() : "Unmapped (PIN " + devicePin + ")");
        record.setDeviceUserId(devicePin);
        record.setPunchTime(punchTime);
        record.setPunchType(resolvePunchType(statusCode, devicePin, punchTime));
        record.setVerifyMode(VERIFY_MODE_LABELS.getOrDefault(verifyCode, "Unknown"));
        record.setDeviceSerialNumber(device.getSerialNumber());
        record.setDeviceName(device.getDeviceName());
        // tenant: inherit from device if present
        record.setCompany(device.getCompany());
        record.setRawLine(line);

        return attendanceRecordRepository.save(record);
    }

    private String resolvePunchType(String statusCode, String devicePin, LocalDateTime punchTime) {
        if (statusCode != null && STATUS_TO_PUNCH_TYPE.containsKey(statusCode)) {
            return STATUS_TO_PUNCH_TYPE.get(statusCode);
        }
        LocalDateTime startOfDay = punchTime.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long punchesToday = attendanceRecordRepository.countByDeviceUserIdAndPunchTimeBetween(devicePin, startOfDay, endOfDay);
        return (punchesToday % 2 == 0) ? "IN" : "OUT";
    }
}
