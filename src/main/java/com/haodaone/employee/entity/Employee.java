package com.haodaone.employee.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.entity.Team;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * The HR profile record - separate from User (the login identity, see
 * user.entity.User). Not every employee needs system access on day one
 * (onboarding may create the HR record before provisioning login), and the
 * link is nullable so historical employee records survive even if their
 * User account is later removed.
 *
 * status drives the employee lifecycle: Active -> On Leave / Notice Period
 * -> Resigned / Terminated (see EmploymentStatus.java). Per platform convention, employees are never
 * hard-deleted once they exist - status changes and soft-delete (BaseEntity.deleted)
 * are how records leave the active roster while everything that references
 * them (attendance, leave, audit trail) stays intact.
 */
@Entity
@Table(name = "employee", uniqueConstraints = {
        @UniqueConstraint(columnNames = "employee_id"),
        @UniqueConstraint(columnNames = "email")
})
public class Employee extends BaseEntity {

    /**
     * Shared with HaodaAsset: this is its "employee_id" column (its own
     * human-readable employee code), reused here instead of adding a
     * duplicate column. Already unique + not null in the shared schema.
     */
    @Column(name = "employee_id", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Free-text by design for Phase 1 - not constrained to a fixed enum so orgs aren't forced into a binary model. */
    @Column(length = 30)
    private String gender;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    /** FULL_TIME, PART_TIME, CONTRACT, INTERN */
    @Column(name = "employment_type", nullable = false, length = 20)
    private String employmentType = "FULL_TIME";

    /**
     * "Active", "On Leave", "Notice Period", "Resigned", "Terminated" -
     * see EmploymentStatus.java in this package. Reuses HaodaAsset's
     * existing employment_status column instead of adding a duplicate,
     * and matches HaodaAsset's own Title Case values exactly since that
     * app reads/writes and filters on this same column.
     */
    @Column(name = "employment_status", nullable = false, length = 30)
    private String status = EmploymentStatus.ACTIVE;

    public String getStatus() {
        return EmploymentStatus.normalize(status);
    }

    public void setStatus(String status) {
        this.status = EmploymentStatus.normalize(status);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    /** Nullable - see class javadoc. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Tenant/company this employee belongs to (nullable for legacy rows until migration is applied). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    @Column(length = 300)
    private String address;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    /**
     * The PIN/user-id this employee is enrolled under on the biometric
     * device(s) - nullable since biometric enrollment typically happens
     * after HR onboarding, not at the same moment. Unique so two employees
     * can't accidentally share a device identity. See attendance.service.
     * AttendanceIngestService for how incoming punches resolve this.
     */
    @Column(name = "biometric_device_user_id", unique = true, length = 30)
    private String biometricDeviceUserId;

    public String getBiometricDeviceUserId() {
        return biometricDeviceUserId;
    }

    public void setBiometricDeviceUserId(String biometricDeviceUserId) {
        this.biometricDeviceUserId = biometricDeviceUserId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Employee getReportingManager() {
        return reportingManager;
    }

    public void setReportingManager(Employee reportingManager) {
        this.reportingManager = reportingManager;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public com.haodaone.company.entity.Company getCompany() {
        return company;
    }

    public void setCompany(com.haodaone.company.entity.Company company) {
        this.company = company;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
