package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.DeviceDTO;
import com.haodaone.attendance.entity.Device;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Device Dashboard - online/offline status, last sync, rename. Manual "resync" and per-device config are later refinements. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;

    public DeviceController(DeviceRepository deviceRepository, AuditLogService auditLogService) {
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    public List<DeviceDTO> listAll() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(companyId).stream().map(DeviceDTO::from).toList();
    }

    @PatchMapping("/{id}/rename")
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    public DeviceDTO rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        Device device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));
        String newName = body.get("deviceName");
        device.setDeviceName(newName);
        Device saved = deviceRepository.save(device);
        auditLogService.log("Device", saved.getId(), "UPDATE", "Renamed device to '" + newName + "'");
        return DeviceDTO.from(saved);
    }
}
