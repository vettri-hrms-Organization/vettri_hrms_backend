package com.haodaone.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.security.JwtService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeDocumentUploadSecurityTest {

    private static final String UPLOAD_PATH = "/api/documents/employee/{employeeId}/upload";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentRepository documentRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmployeeDocumentS3StorageService documentStorageService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void uploadWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(uploadRequest(4268L, "AADHAAR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadWithInvalidJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(uploadRequest(4268L, "AADHAAR").header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtAllowsEmployeeToUploadOwnMultipartDocument() throws Exception {
        Company company = createCompany();
        User user = createUser(company, "employee");
        Employee employee = createEmployee(company, user, "Own", null);
        String token = tokenFor(user);
        stubStorage(company, employee, "AADHAAR");
        commitSetup();

        mockMvc.perform(uploadRequest(employee.getId(), "AADHAAR").header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        verify(documentStorageService).store(any(MultipartFile.class), eq(company.getId()),
                eq(employee.getId()), eq("AADHAAR"));
    }

    @Test
    void hrCannotUploadEmployeeDocumentEvenWithinAuthorizedScope() throws Exception {
        Company company = createCompany();
        Department department = createDepartment(company);
        User hrUser = createUser(company, "department-hr");
        Employee hrEmployee = createEmployee(company, hrUser, "Hr", department);
        Employee target = createEmployee(company, null, "InScope", department);
        grantEmployeeManage(hrUser, company, PermissionScope.DEPARTMENT);
        String token = tokenFor(hrUser);
        stubStorage(company, target, "AADHAAR");
        commitSetup();

        mockMvc.perform(uploadRequest(target.getId(), "AADHAAR").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        verify(documentStorageService, never()).store(any(MultipartFile.class), eq(company.getId()),
                eq(target.getId()), eq("AADHAAR"));
    }

    @Test
    void authenticatedEmployeeCannotUploadForAnotherEmployee() throws Exception {
        Company company = createCompany();
        User user = createUser(company, "employee-no-scope");
        createEmployee(company, user, "Requester", null);
        Employee target = createEmployee(company, null, "Other", null);
        String token = tokenFor(user);
        commitSetup();

        mockMvc.perform(uploadRequest(target.getId(), "AADHAAR").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCannotUploadOutsideDepartmentOrTenantScope() throws Exception {
        Company company = createCompany();
        Company otherCompany = createCompany();
        Department permittedDepartment = createDepartment(company);
        Department otherDepartment = createDepartment(company);
        User hrUser = createUser(company, "limited-hr");
        createEmployee(company, hrUser, "LimitedHr", permittedDepartment);
        Employee outsideDepartment = createEmployee(company, null, "OtherDepartment", otherDepartment);
        Employee crossTenantEmployee = createEmployee(otherCompany, null, "CrossTenant", null);
        grantEmployeeManage(hrUser, company, PermissionScope.DEPARTMENT);
        String token = tokenFor(hrUser);
        commitSetup();

        mockMvc.perform(uploadRequest(outsideDepartment.getId(), "AADHAAR").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(uploadRequest(crossTenantEmployee.getId(), "AADHAAR").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedMultipartUploadStillValidatesDocumentFields() throws Exception {
        Company company = createCompany();
        User employeeUser = createUser(company, "validation-employee");
        Employee employee = createEmployee(company, employeeUser, "ValidationEmployee", null);
        String token = tokenFor(employeeUser);
        commitSetup();

        mockMvc.perform(uploadRequest(employee.getId(), "PASSPORT")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCanReviewPendingDocumentWithinDepartmentScope() throws Exception {
        Company company = createCompany();
        Department department = createDepartment(company);
        User employeeUser = createUser(company, "reviewee");
        Employee employee = createEmployee(company, employeeUser, "Reviewee", department);
        User hrUser = createUser(company, "authorized-reviewer");
        createEmployee(company, hrUser, "AuthorizedReviewer", department);
        grantEmployeeManage(hrUser, company, PermissionScope.DEPARTMENT);
        String employeeToken = tokenFor(employeeUser);
        String hrToken = tokenFor(hrUser);
        stubStorage(company, employee, "AADHAAR");
        commitSetup();

        long documentId = uploadAndReadDocumentId(employee, employeeToken);

        mockMvc.perform(post("/api/documents/{id}/review", documentId)
                        .header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        assertEquals(EmployeeDocument.STATUS_APPROVED,
                documentRepository.findById(documentId).orElseThrow().getStatus());
    }

    @Test
    void hrCannotReviewDocumentOutsideAuthorizedDepartmentScope() throws Exception {
        Company company = createCompany();
        Department hrDepartment = createDepartment(company);
        Department otherDepartment = createDepartment(company);
        User employeeUser = createUser(company, "out-of-scope-reviewee");
        Employee employee = createEmployee(company, employeeUser, "OutOfScopeReviewee", otherDepartment);
        User hrUser = createUser(company, "out-of-scope-reviewer");
        createEmployee(company, hrUser, "ScopedReviewer", hrDepartment);
        grantEmployeeManage(hrUser, company, PermissionScope.DEPARTMENT);
        String employeeToken = tokenFor(employeeUser);
        String hrToken = tokenFor(hrUser);
        stubStorage(company, employee, "AADHAAR");
        commitSetup();

        long documentId = uploadAndReadDocumentId(employee, employeeToken);

        mockMvc.perform(post("/api/documents/{id}/review", documentId)
                        .header("Authorization", bearer(hrToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());

        assertEquals(EmployeeDocument.STATUS_PENDING_REVIEW,
                documentRepository.findById(documentId).orElseThrow().getStatus());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder uploadRequest(
            Long employeeId, String documentType) {
        return multipart(UPLOAD_PATH, employeeId)
                .file(new MockMultipartFile("file", "identity.pdf", "application/pdf", new byte[]{1, 2, 3}))
                .param("documentType", documentType);
    }

    private long uploadAndReadDocumentId(Employee employee, String token) throws Exception {
        String response = mockMvc.perform(uploadRequest(employee.getId(), "AADHAAR")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Document Company " + UUID.randomUUID());
        return companyRepository.save(company);
    }

    private Department createDepartment(Company company) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Department department = new Department();
        department.setName("Documents " + suffix);
        department.setCode("D" + suffix);
        department.setCompany(company);
        return departmentRepository.save(department);
    }

    private User createUser(Company company, String label) {
        String username = label + "-" + UUID.randomUUID() + "@documents.example";
        User user = new User();
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName(label);
        user.setPasswordHash(passwordEncoder.encode("unused-test-password"));
        user.setCompany(company);
        user.setRoles(new HashSet<>());
        return userRepository.save(user);
    }

    private Employee createEmployee(Company company, User user, String label, Department department) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("E" + suffix);
        employee.setFirstName(label);
        employee.setLastName("Document");
        employee.setEmail(label.toLowerCase() + "-" + suffix + "@documents.example");
        employee.setDateOfJoining(LocalDate.now().minusMonths(1));
        employee.setCompany(company);
        employee.setDepartment(department);
        employee.setUser(user);
        return employeeRepository.save(employee);
    }

    private void grantEmployeeManage(User user, Company company, PermissionScope scope) {
        Permission permission = permissionRepository.findByCode("EMPLOYEE_MANAGE")
                .orElseGet(() -> {
                    Permission created = new Permission();
                    created.setCode("EMPLOYEE_MANAGE");
                    created.setDescription("Manage employee documents");
                    created.setModule("EMPLOYEE");
                    return permissionRepository.save(created);
                });

        Role role = new Role();
        role.setName("DOCUMENT_HR_" + UUID.randomUUID().toString().replace("-", ""));
        role.setLabel("Document HR");
        role.setCompany(company);
        role.setPermissions(new HashSet<>(Set.of(permission)));
        RolePermissionScope permissionScope = new RolePermissionScope();
        permissionScope.setRole(role);
        permissionScope.setPermission(permission);
        permissionScope.setScope(scope);
        role.setPermissionScopes(new HashSet<>(Set.of(permissionScope)));
        role = roleRepository.save(role);
        user.setRoles(new HashSet<>(Set.of(role)));
        userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateAccessToken(user.getUsername(),
                user.getRoles().stream().map(Role::getName).toList());
    }

    private void stubStorage(Company company, Employee employee, String documentType) {
        MockMultipartFile file = new MockMultipartFile("file", "identity.pdf", "application/pdf", new byte[]{1, 2, 3});
        String key = "employee-documents/" + company.getId() + "/" + employee.getId() + "/" + documentType + "/test.pdf";
        when(documentStorageService.store(any(MultipartFile.class), eq(company.getId()),
                eq(employee.getId()), eq(documentType)))
                .thenReturn(new EmployeeDocumentS3StorageService.StoredFile(
                        key, file.getOriginalFilename(), file.getSize(), file.getContentType()));
    }

    private void commitSetup() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
