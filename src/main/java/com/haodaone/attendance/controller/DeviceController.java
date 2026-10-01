package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.DeviceDTO;
import com.haodaone.attendance.entity.Device;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
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
    private final CompanyRepository companyRepository;

    public DeviceController(DeviceRepository deviceRepository, AuditLogService auditLogService,
                            CompanyRepository companyRepository) {
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
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

    @PostMapping
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    public DeviceDTO register(@Valid @RequestBody DeviceDTO.RegisterRequest request) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        String serialNumber = request.serialNumber().trim();
        if (deviceRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new BadRequestException("A device with this serial number is already registered");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        Device device = new Device();
        device.setSerialNumber(serialNumber);
        device.setDeviceName(request.deviceName().trim());
        device.setCompany(company);
        Device saved = deviceRepository.save(device);
        auditLogService.log("Device", saved.getId(), "CREATE", "Registered biometric device '" + serialNumber + "'");
        return DeviceDTO.from(saved);
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
