package com.haodaone.company.service;

import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.dto.CompanyAdminDTO;
import com.haodaone.company.dto.SubscriptionAdminDTO;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CompanyAdminService {
    private final CompanyRepository companies;
    private final SubscriptionRepository subscriptions;
    public CompanyAdminService(CompanyRepository companies, SubscriptionRepository subscriptions) { this.companies = companies; this.subscriptions = subscriptions; }
    @Transactional(readOnly = true) public List<CompanyAdminDTO> companies() { return companies.findAll().stream().filter(c -> !c.isDeleted()).map(CompanyAdminDTO::from).toList(); }
    @Transactional public CompanyAdminDTO createCompany(CompanyAdminDTO.WriteRequest request) { Company c = new Company(); apply(c, request); return CompanyAdminDTO.from(companies.save(c)); }
    @Transactional public CompanyAdminDTO updateCompany(Long id, CompanyAdminDTO.WriteRequest request) { Company c = company(id); apply(c, request); return CompanyAdminDTO.from(companies.save(c)); }
    @Transactional(readOnly = true) public List<SubscriptionAdminDTO> subscriptions() { return subscriptions.findAll().stream().filter(s -> !s.isDeleted()).map(SubscriptionAdminDTO::from).toList(); }
    @Transactional public SubscriptionAdminDTO createSubscription(Long companyId, SubscriptionAdminDTO.WriteRequest request) { Subscription s = new Subscription(); s.setCompany(company(companyId)); apply(s, request); return SubscriptionAdminDTO.from(subscriptions.save(s)); }
    @Transactional public SubscriptionAdminDTO updateSubscription(Long id, SubscriptionAdminDTO.WriteRequest request) { Subscription s = subscriptions.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id)); apply(s, request); return SubscriptionAdminDTO.from(subscriptions.save(s)); }
    private void apply(Subscription s, SubscriptionAdminDTO.WriteRequest r) { s.setPlan(r.plan()); s.setStatus(r.status()); s.setEmployeeLimit(r.employeeLimit()); s.setDeviceLimit(r.deviceLimit()); s.setStartDate(r.startDate()); s.setRenewalDate(r.renewalDate()); s.setAmount(r.amount()); }
    private void apply(Company c, CompanyAdminDTO.WriteRequest request) { c.setName(request.name().trim()); c.setDomain(request.domain()); if (request.productiveThresholdPercent() != null) c.setProductiveThresholdPercent(request.productiveThresholdPercent()); if (request.neutralThresholdPercent() != null) c.setNeutralThresholdPercent(request.neutralThresholdPercent()); }
    private Company company(Long id) { return companies.findById(id).filter(c -> !c.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id)); }
}