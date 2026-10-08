package com.haodaone.assistant;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AssistantContextResolver {
    private final EmployeeRepository employeeRepository;

    public AssistantContextResolver(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public AssistantContext resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new AccessDeniedException("Authentication required");
        }

        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new AccessDeniedException("Workspace context required");

        Set<String> authorities = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }

        Employee employee = employeeRepository.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(candidate -> candidate.getCompany() != null
                        && companyId.equals(candidate.getCompany().getId()))
                .orElse(null);
        return new AssistantContext(
                principal.getId(),
                employee == null ? null : employee.getId(),
                companyId,
                authorities,
                Set.copyOf(principal.getRoleNames()),
                employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee == null || employee.getTeam() == null ? null : employee.getTeam().getName(),
                employee == null || employee.getDesignation() == null ? null : employee.getDesignation().getTitle()
        );
    }
}
