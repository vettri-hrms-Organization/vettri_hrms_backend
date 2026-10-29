package com.haodaone.monitoring.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.notifications.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * HR notifications (leave approved, payslip ready, announcements...) shown as Windows popups by
 * the agent tray, for the employee the laptop is assigned to. Read-only: popping up does not mark
 * a notification read; the employee still sees it in the portal inbox.
 */
@Service
public class AgentNotificationService {

    private static final int MAX_AGE_HOURS = 24;
    private static final int LIMIT = 5;

    private final MonitoredDeviceRepository deviceRepository;
    private final NotificationService notificationService;

    public AgentNotificationService(MonitoredDeviceRepository deviceRepository, NotificationService notificationService) {
        this.deviceRepository = deviceRepository;
        this.notificationService = notificationService;
    }

    public record Item(Long id, String type, String title, String message, String priority, LocalDateTime createdAt) { }

    @Transactional(readOnly = true)
    public List<Item> newSince(MonitoredDevice authenticatedDevice, long afterId) {
        MonitoredDevice device = deviceRepository.findById(authenticatedDevice.getId()).orElse(null);
        if (device == null || device.getCompany() == null) return List.of();
        Employee employee = device.getEmployee();
        if (employee == null || employee.getUser() == null) return List.of();
        return notificationService.recentUnreadForUser(device.getCompany().getId(), employee.getUser().getId(),
                        Math.max(0, afterId), MAX_AGE_HOURS, LIMIT)
                .stream().map(AgentNotificationService::toItem).toList();
    }

    private static Item toItem(Map<String, Object> row) {
        Object created = row.get("created_at");
        LocalDateTime createdAt = created instanceof java.sql.Timestamp ts ? ts.toLocalDateTime()
                : created instanceof LocalDateTime ldt ? ldt : null;
        return new Item(((Number) row.get("id")).longValue(), (String) row.get("type"), (String) row.get("title"),
                (String) row.get("message"), (String) row.get("priority"), createdAt);
    }
}
