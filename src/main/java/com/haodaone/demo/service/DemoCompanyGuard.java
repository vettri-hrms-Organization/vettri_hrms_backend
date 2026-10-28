package com.haodaone.demo.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import org.springframework.stereotype.Component;

/** Cheap "is this a sales demo workspace?" check used to suppress outbound email / WhatsApp. */
@Component
public class DemoCompanyGuard {

    private final CompanyRepository companies;

    public DemoCompanyGuard(CompanyRepository companies) {
        this.companies = companies;
    }

    public boolean isDemo(Long companyId) {
        if (companyId == null) return false;
        return companies.findById(companyId).map(Company::isDemo).orElse(false);
    }
}
