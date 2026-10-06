package com.haodaone.org.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.dto.OrganizationStructureDTO;
import com.haodaone.org.dto.OrganizationStructureDTO.DepartmentNode;
import com.haodaone.org.dto.OrganizationStructureDTO.EmployeeNode;
import com.haodaone.org.dto.OrganizationStructureDTO.TeamNode;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Team;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OrganizationStructureService {

    private static final String UNASSIGNED_DEPARTMENT = "Unassigned Department";
    private static final String UNASSIGNED_TEAM = "Unassigned Team";

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;

    public OrganizationStructureService(
            CompanyRepository companyRepository,
            DepartmentRepository departmentRepository,
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository) {
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationStructureDTO getStructure() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));
        List<Department> departments = departmentRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId);
        List<Team> teams = teamRepository.findOrganizationStructureByCompany_IdAndDeletedFalseOrderByNameAsc(companyId);
        List<Employee> employees = employeeRepository.findOrganizationStructureByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId);

        Map<Long, Department> companyDepartments = new LinkedHashMap<>();
        departments.forEach(department -> companyDepartments.put(department.getId(), department));
        Map<Long, Team> companyTeams = new LinkedHashMap<>();
        teams.forEach(team -> companyTeams.put(team.getId(), team));
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        employees.forEach(employee -> employeeNames.put(employee.getId(), employee.getFullName()));
        Map<Long, EmployeeNode> employeeNodes = new LinkedHashMap<>();
        employees.forEach(employee -> employeeNodes.put(employee.getId(),
                toEmployeeNode(employee, employeeNames, companyDepartments, companyTeams)));

        Map<Long, DepartmentBuilder> departmentNodes = new LinkedHashMap<>();
        departments.forEach(department -> departmentNodes.put(department.getId(), new DepartmentBuilder(
                department.getId(), department.getName())));

        DepartmentBuilder unassignedDepartment = new DepartmentBuilder(null, UNASSIGNED_DEPARTMENT);
        Map<Long, TeamBuilder> teamNodes = new LinkedHashMap<>();
        for (Team team : teams) {
            Long departmentId = team.getDepartment() == null ? null : team.getDepartment().getId();
            DepartmentBuilder parent = departmentId == null ? unassignedDepartment : departmentNodes.get(departmentId);
            if (parent == null) {
                parent = unassignedDepartment;
            }
            Long leadEmployeeId = employeeNodes.containsKey(team.getLeadEmployeeId())
                    ? team.getLeadEmployeeId() : null;
            TeamBuilder node = new TeamBuilder(team.getId(), team.getName(), leadEmployeeId);
            parent.teams.put(team.getId(), node);
            teamNodes.put(team.getId(), node);
        }

        for (Employee employee : employees) {
            Long employeeDepartmentId = employee.getDepartment() == null ? null : employee.getDepartment().getId();
            DepartmentBuilder department = employeeDepartmentId == null
                    ? unassignedDepartment : departmentNodes.get(employeeDepartmentId);
            if (department == null) {
                department = unassignedDepartment;
            }

            Team teamEntity = employee.getTeam();
            TeamBuilder team = teamEntity == null ? null : teamNodes.get(teamEntity.getId());
            Long teamDepartmentId = teamEntity == null || teamEntity.getDepartment() == null
                    ? null : teamEntity.getDepartment().getId();
            if (team == null || !Objects.equals(employeeDepartmentId, teamDepartmentId)
                    || (employeeDepartmentId != null && department == unassignedDepartment)) {
                team = department.unassignedTeam();
            }
            team.employees.add(employeeNodes.get(employee.getId()));
            department.employeeCount++;
        }

        teamNodes.forEach((teamId, team) -> {
            if (team.leadEmployeeId != null) {
                EmployeeNode lead = employeeNodes.get(team.leadEmployeeId);
                if (lead != null) {
                    team.leadEmployeeName = lead.fullName();
                }
            }
        });

        List<DepartmentNode> result = new ArrayList<>();
        departments.forEach(department -> result.add(departmentNodes.get(department.getId()).toDTO()));
        if (!unassignedDepartment.teams.isEmpty()) {
            result.add(unassignedDepartment.toDTO());
        }

        return new OrganizationStructureDTO(
                company.getId(),
                company.getName(),
                employees.size(),
                departments.size(),
                teams.size(),
                List.copyOf(result),
                List.copyOf(employeeNodes.values()));
    }

    private EmployeeNode toEmployeeNode(
            Employee employee,
            Map<Long, String> employeeNames,
            Map<Long, Department> companyDepartments,
            Map<Long, Team> companyTeams) {
        Long managerId = employee.getReportingManager() == null ? null : employee.getReportingManager().getId();
        String managerName = managerId == null ? null : employeeNames.get(managerId);
        if (managerName == null) {
            managerId = null;
        }
        Long departmentId = employee.getDepartment() == null ? null : employee.getDepartment().getId();
        Department department = departmentId == null ? null : companyDepartments.get(departmentId);
        if (department == null) {
            departmentId = null;
        }
        Long teamId = employee.getTeam() == null ? null : employee.getTeam().getId();
        Team team = teamId == null ? null : companyTeams.get(teamId);
        if (team == null) {
            teamId = null;
        }
        String designation = employee.getDesignation() == null ? null : employee.getDesignation().getTitle();
        if (employee.getDesignation() != null
                && employee.getDesignation().getDepartment() != null
                && !companyDepartments.containsKey(employee.getDesignation().getDepartment().getId())) {
            designation = null;
        }
        return new EmployeeNode(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getProfilePhotoUrl(),
                designation,
                employee.getStatus(),
                departmentId,
                department == null ? null : department.getName(),
                teamId,
                team == null ? null : team.getName(),
                managerId,
                managerName);
    }

    private static final class DepartmentBuilder {
        private final Long id;
        private final String name;
        private final Map<Long, TeamBuilder> teams = new LinkedHashMap<>();
        private long employeeCount;
        private TeamBuilder unassignedTeam;

        private DepartmentBuilder(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private TeamBuilder unassignedTeam() {
            if (unassignedTeam == null) {
                unassignedTeam = new TeamBuilder(null, UNASSIGNED_TEAM, null);
                teams.put(null, unassignedTeam);
            }
            return unassignedTeam;
        }

        private DepartmentNode toDTO() {
            List<TeamNode> teamNodes = teams.values().stream().map(TeamBuilder::toDTO).toList();
            return new DepartmentNode(id, name, employeeCount, teams.values().stream()
                    .filter(team -> team.id != null).count(), teamNodes);
        }
    }

    private static final class TeamBuilder {
        private final Long id;
        private final String name;
        private final Long leadEmployeeId;
        private String leadEmployeeName;
        private final List<EmployeeNode> employees = new ArrayList<>();

        private TeamBuilder(Long id, String name, Long leadEmployeeId) {
            this.id = id;
            this.name = name;
            this.leadEmployeeId = leadEmployeeId;
        }

        private TeamNode toDTO() {
            return new TeamNode(id, name, employees.size(), leadEmployeeId, leadEmployeeName, List.copyOf(employees));
        }
    }
}
