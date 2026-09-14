package com.haodaone.requirements.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.requirements.dto.RequirementDTO;
import com.haodaone.requirements.entity.Requirement;
import com.haodaone.requirements.entity.RequirementStatus;
import com.haodaone.requirements.repository.RequirementRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class RequirementService {
    private final RequirementRepository repository; private final CompanyRepository companyRepository; private final UserRepository userRepository;
    public RequirementService(RequirementRepository repository, CompanyRepository companyRepository, UserRepository userRepository) { this.repository = repository; this.companyRepository = companyRepository; this.userRepository = userRepository; }
    @Transactional(readOnly = true) public java.util.List<RequirementDTO> list(RequirementStatus status) { Long tenant = tenant(); return (status == null ? repository.findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(tenant) : repository.findAllByCompany_IdAndStatusAndDeletedFalseOrderByCreatedAtDesc(tenant, status)).stream().map(RequirementDTO::from).toList(); }
    @Transactional public RequirementDTO create(RequirementDTO.WriteRequest request, CustomUserPrincipal principal) { Requirement r = new Requirement(); r.setCompany(company()); apply(r, request); return RequirementDTO.from(repository.save(r)); }
    @Transactional public RequirementDTO update(Long id, RequirementDTO.WriteRequest request) { Requirement r = find(id); apply(r, request); return RequirementDTO.from(repository.save(r)); }
    @Transactional public RequirementDTO changeStatus(Long id, RequirementDTO.StatusRequest request, User actor) { if (request.status() == null) throw new BadRequestException("Status is required"); Requirement r = find(id); if (request.status() == RequirementStatus.CLOSED && (request.closeReason() == null || request.closeReason().isBlank())) throw new BadRequestException("Close reason is required"); r.setStatus(request.status()); r.setCloseReason(request.status() == RequirementStatus.CLOSED ? request.closeReason().trim() : null); r.setClosedBy(request.status() == RequirementStatus.CLOSED ? actor : null); r.setClosedAt(request.status() == RequirementStatus.CLOSED ? LocalDateTime.now() : null); return RequirementDTO.from(repository.save(r)); }
    @Transactional public void delete(Long id) { Requirement r = find(id); r.setDeleted(true); r.setDeletedAt(LocalDateTime.now()); repository.save(r); }
    private void apply(Requirement r, RequirementDTO.WriteRequest request) { r.setTitle(request.title().trim()); r.setDescription(request.description()); r.setDueDate(request.dueDate()); r.setAssignedTo(request.assignedToUserId() == null ? null : userRepository.findByIdAndCompanyIdAndDeletedFalse(request.assignedToUserId(), tenant()).orElseThrow(() -> new BadRequestException("Assigned user is not in this company"))); }
    private Requirement find(Long id) { return repository.findByIdAndCompany_IdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + id)); }
    private Long tenant() { Long id = TenantContext.getCurrentTenant(); if (id == null) throw new BadRequestException("Company context is required"); return id; }
    private Company company() { return companyRepository.findById(tenant()).orElseThrow(() -> new ResourceNotFoundException("Company not found")); }
}