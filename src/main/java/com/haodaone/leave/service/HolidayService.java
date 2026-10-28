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
    private com.haodaone.attendance.repository.OfficeLocationRepository officeLocationRepository;

    public HolidayService(HolidayRepository holidayRepository, AuditLogService auditLogService, CompanyRepository companyRepository) {
        this.holidayRepository = holidayRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOfficeLocationRepository(com.haodaone.attendance.repository.OfficeLocationRepository officeLocationRepository) {
        this.officeLocationRepository = officeLocationRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayDTO> listAll() {
        Long companyId = requiredTenant();
        java.util.Map<Long, String> names = locationNames(companyId);
        return holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, java.time.LocalDate.of(1900, 1, 1), java.time.LocalDate.of(2200, 12, 31)).stream()
                .sorted(java.util.Comparator.comparing(Holiday::getDate))
                .map(h -> HolidayDTO.from(h, names)).toList();
    }

    /** Office location id -> name for the company (empty when the attendance module is absent). */
    public java.util.Map<Long, String> locationNames(Long companyId) {
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        if (officeLocationRepository != null) {
            officeLocationRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId)
                    .forEach(l -> names.put(l.getId(), l.getName()));
        }
        return names;
    }

    @Transactional
    public HolidayDTO create(HolidayDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        Holiday holiday = new Holiday();
        holiday.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found")));
        apply(holiday, request, companyId);

        Holiday saved = holidayRepository.save(holiday);
        auditLogService.log("Holiday", saved.getId(), "CREATE", "Added " + saved.getHolidayType().toLowerCase(java.util.Locale.ROOT)
                + " holiday '" + saved.getName() + "' on " + saved.getDate()
                + (saved.getLocationIds().isEmpty() ? " (all locations)" : " (locations " + saved.getLocationIds() + ")"));
        return HolidayDTO.from(saved, locationNames(companyId));
    }

    @Transactional
    public HolidayDTO update(Long id, HolidayDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        Holiday holiday = holidayRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new com.haodaone.common.exception.ResourceNotFoundException("Holiday not found"));
        apply(holiday, request, companyId);
        Holiday saved = holidayRepository.save(holiday);
        auditLogService.log("Holiday", saved.getId(), "UPDATE", "Updated holiday '" + saved.getName() + "' on " + saved.getDate()
                + " - " + saved.getHolidayType() + (saved.getLocationIds().isEmpty() ? ", all locations" : ", locations " + saved.getLocationIds()));
        return HolidayDTO.from(saved, locationNames(companyId));
    }

    /** Validates and copies a create/update request onto {@code holiday}. */
    private void apply(Holiday holiday, HolidayDTO.CreateRequest request, Long companyId) {
        String type = request.getHolidayType() == null || request.getHolidayType().isBlank()
                ? Holiday.MANDATORY : request.getHolidayType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!Holiday.MANDATORY.equals(type) && !Holiday.OPTIONAL.equals(type)) {
            throw new BadRequestException("Holiday type must be MANDATORY or OPTIONAL");
        }
        java.util.Set<Long> locations = new java.util.HashSet<>();
        if (request.getLocationIds() != null) {
            for (Long locationId : request.getLocationIds()) {
                if (locationId == null) continue;
                if (officeLocationRepository != null
                        && officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(locationId, companyId).isEmpty()) {
                    throw new BadRequestException("Unknown office location: " + locationId);
                }
                locations.add(locationId);
            }
        }
        // Two holidays may share a date only when they apply to different locations.
        for (Holiday other : holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, request.getDate(), request.getDate())) {
            if (holiday.getId() != null && holiday.getId().equals(other.getId())) continue;
            java.util.Set<Long> otherLocations = other.getLocationIds() == null ? java.util.Set.of() : other.getLocationIds();
            boolean overlaps = locations.isEmpty() || otherLocations.isEmpty()
                    || otherLocations.stream().anyMatch(locations::contains);
            if (overlaps) {
                throw new BadRequestException("A holiday ('" + other.getName() + "') is already recorded on " + request.getDate()
                        + " for the same location(s)");
            }
        }
        holiday.setName(request.getName().trim());
        holiday.setDate(request.getDate());
        holiday.setHolidayType(type);
        holiday.setLocationIds(locations);
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
