package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.leave.dto.HolidayDTO;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.haodaone.tenant.TenantContext;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;

    public HolidayService(HolidayRepository holidayRepository, AuditLogService auditLogService, CompanyRepository companyRepository) {
        this.holidayRepository = holidayRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    public List<HolidayDTO> listAll() {
        Long companyId = requiredTenant();
        return holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, java.time.LocalDate.of(1900, 1, 1), java.time.LocalDate.of(2200, 12, 31)).stream().map(HolidayDTO::from).toList();
    }

    @Transactional
    public HolidayDTO create(HolidayDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        if (holidayRepository.existsByCompany_IdAndDateAndDeletedFalse(companyId, request.getDate())) {
            throw new BadRequestException("A holiday is already recorded on " + request.getDate());
        }
        Holiday holiday = new Holiday();
        holiday.setName(request.getName());
        holiday.setDate(request.getDate());
        holiday.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found")));

        Holiday saved = holidayRepository.save(holiday);
        auditLogService.log("Holiday", saved.getId(), "CREATE", "Added holiday '" + saved.getName() + "' on " + saved.getDate());
        return HolidayDTO.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = holidayRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant()).orElseThrow();
        holiday.setDeleted(true);
        holidayRepository.save(holiday);
        auditLogService.log("Holiday", id, "DELETE", "Removed holiday '" + holiday.getName() + "'");
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
