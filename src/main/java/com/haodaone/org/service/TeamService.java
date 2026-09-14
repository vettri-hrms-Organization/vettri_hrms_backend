package com.haodaone.org.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.dto.TeamDTO;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Team;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public TeamService(TeamRepository teamRepository, DepartmentRepository departmentRepository,
                        EmployeeRepository employeeRepository, CompanyRepository companyRepository,
                        AuditLogService auditLogService) {
        this.teamRepository = teamRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    public List<TeamDTO> listAll() {
        Long companyId = requiredTenant();
        return teamRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(this::toEnrichedDTO)
                .toList();
    }

    @Transactional
    public TeamDTO create(TeamDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        if (teamRepository.existsByCompany_IdAndNameAndDeletedFalse(companyId, request.getName())) {
            throw new BadRequestException("Team name '" + request.getName() + "' is already in use in this company");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));

        Team team = new Team();
        team.setName(request.getName());
        team.setCompany(company);
        team.setLeadEmployeeId(request.getLeadEmployeeId());
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDepartmentId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown department: " + request.getDepartmentId()));
            team.setDepartment(department);
        }

        Team saved = teamRepository.save(team);
        auditLogService.log("Team", saved.getId(), "CREATE", "Created team '" + saved.getName() + "'");
        return toEnrichedDTO(saved);
    }

    private TeamDTO toEnrichedDTO(Team team) {
        TeamDTO dto = TeamDTO.from(team);
        dto.setMemberCount(employeeRepository.countByTeamIdAndDeletedFalse(team.getId()));
        if (team.getLeadEmployeeId() != null) {
            employeeRepository.findById(team.getLeadEmployeeId())
                    .map(Employee::getFullName)
                    .ifPresent(dto::setLeadEmployeeName);
        }
        return dto;
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenant;
    }
}
