package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.common.exception.EmailDeliveryException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.dto.InvitationResponse;
import com.haodaone.employee.dto.InvitationValidationResponse;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmployeeInvitation;
import com.haodaone.employee.repository.EmployeeInvitationRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeInvitationService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeInvitationService.class);
    private static final String PENDING = "PENDING";
    private static final String USED = "USED";
    private static final String REVOKED = "REVOKED";
    private static final String EXPIRED = "EXPIRED";

    private final EmployeeRepository employeeRepository;
    private final EmployeeInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.account-invitation.expiry-hours:48}")
    private long expiryHours;

    public EmployeeInvitationService(EmployeeRepository employeeRepository,
                                     EmployeeInvitationRepository invitationRepository,
                                     UserRepository userRepository, RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder, EmailService emailService,
                                     AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public InvitationResponse sendInvitation(Long employeeId) {
        return issueInvitation(employeeId, false);
    }

    @Transactional
    public InvitationResponse resendInvitation(Long employeeId) {
        return issueInvitation(employeeId, true);
    }

    private InvitationResponse issueInvitation(Long employeeId, boolean resend) {
        Employee employee = findEmployee(employeeId);
        userRepository.lockEmployeeForCurrentTransaction(employeeId);
        String email = normalizeAndValidateEmail(employee);

        EmployeeInvitation pending = invitationRepository.findByEmployee_IdAndStatus(employeeId, PENDING).orElse(null);
        if (!resend && pending != null) {
            throw new ConflictException("This employee already has a pending invitation");
        }

        ensurePendingUser(employee);

        String rawToken = newToken();
        if (pending != null) {
            pending.setStatus(REVOKED);
            invitationRepository.save(pending);
        }
        EmployeeInvitation invitation = new EmployeeInvitation();
        invitation.setEmployee(employee);
        invitation.setTokenHash(hash(rawToken));
        invitation.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        invitation.setUsedAt(null);
        invitation.setStatus(PENDING);
        invitationRepository.save(invitation);

        if (!emailService.sendEmployeeInvitationEmail(email, employee.getFullName(),
                employee.getEmployeeCode(), rawToken, invitation.getExpiresAt())) {
            invitation.setStatus(REVOKED);
            throw new EmailDeliveryException("Invitation email could not be sent. Please try again.");
        }

        auditLogService.log("Employee", employee.getId(), resend ? "INVITATION_RESENT" : "INVITATION_SENT",
                "Invitation sent to '" + employee.getFullName() + "'");
        return new InvitationResponse("Invitation sent successfully", invitation.getExpiresAt().toString());
    }

    @Transactional(readOnly = true)
    public InvitationValidationResponse validate(String rawToken) {
        EmployeeInvitation invitation = findPending(rawToken);
        Employee employee = invitation.getEmployee();
        rejectIfActivated(employee);
        return new InvitationValidationResponse(true, employee.getFullName(), employee.getEmail(), invitation.getExpiresAt().toString());
    }

    @Transactional
    public void activate(String rawToken, String password) {
        String tokenFingerprint = fingerprint(rawToken);
        log.info("Activation started token={}", tokenFingerprint);
        try {
            EmployeeInvitation invitation = findPending(rawToken, true);
            log.info("Activation token lookup succeeded token={} invitationId={} status={}",
                    tokenFingerprint, invitation.getId(), invitation.getStatus());

            Employee employee = invitation.getEmployee();
            log.info("Activation employee lookup succeeded token={} employeeId={} hasUser={}",
                    tokenFingerprint, employee.getId(), employee.getUser() != null);
            rejectIfActivated(employee);
            User user = employee.getUser();
            if (user == null) {
                throw new BadRequestException("This invitation is not associated with an account");
            }
            log.info("Activation user lookup succeeded token={} userId={} accountStatus={} active={} mustChangePassword={}",
                    tokenFingerprint, user.getId(), user.getAccountStatus(), user.isActive(), user.isMustChangePassword());
            if ("ACTIVE".equals(user.getAccountStatus()) && user.isActive() && !user.isMustChangePassword()) {
                throw new ConflictException("This employee account is already activated");
            }

            log.info("Activation password validation passed token={} passwordLength={}", tokenFingerprint,
                    password == null ? 0 : password.length());
            String encodedPassword = passwordEncoder.encode(password);
            log.info("Activation password encoding succeeded token={}", tokenFingerprint);
            user.setPasswordHash(encodedPassword);
            user.setActive(true);
            user.setMustChangePassword(false);
            user.setAccountStatus("ACTIVE");
            userRepository.save(user);
            log.info("Activation password/account update saved token={} userId={}", tokenFingerprint, user.getId());

            invitation.setUsedAt(LocalDateTime.now());
            invitation.setStatus(USED);
            invitationRepository.save(invitation);
            log.info("Activation invitation/token status update saved token={} invitationId={} status={}",
                    tokenFingerprint, invitation.getId(), invitation.getStatus());
            auditLogService.log("Employee", employee.getId(), "INVITATION_ACCEPTED",
                    "Employee account activated for '" + employee.getFullName() + "'");
            log.info("Activation transaction completed token={} employeeId={}", tokenFingerprint, employee.getId());
        } catch (RuntimeException ex) {
            log.error("Activation failed token={} exception={} message={}", tokenFingerprint,
                    ex.getClass().getName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private String fingerprint(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return "missing";
        return hash(rawToken).substring(0, 12);
    }

    private EmployeeInvitation findPending(String rawToken) {
        return findPending(rawToken, false);
    }

    private EmployeeInvitation findPending(String rawToken, boolean lockForUpdate) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Invitation token is required");
        }
        String tokenHash = hash(rawToken);
        EmployeeInvitation invitation = (lockForUpdate
                ? invitationRepository.findByTokenHashWithEmployeeForUpdate(tokenHash)
                : invitationRepository.findByTokenHashWithEmployee(tokenHash))
                .orElseThrow(() -> new BadRequestException("Invitation link is invalid"));
        if (!PENDING.equals(invitation.getStatus())) {
            if (USED.equals(invitation.getStatus())) {
                throw new ConflictException("This invitation has already been used");
            }
            throw new BadRequestException("Invitation link is invalid");
        }
        if (!invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            invitation.setStatus(EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Invitation has expired");
        }
        return invitation;
    }

    private void rejectIfActivated(Employee employee) {
        User user = employee.getUser();
        if (user != null && "ACTIVE".equals(user.getAccountStatus()) && user.isActive() && !user.isMustChangePassword()) {
            throw new ConflictException("This employee account is already activated");
        }
    }

    private User ensurePendingUser(Employee employee) {
        String email = employee.getEmail();
        User linkedUser = employee.getUser();
        User userByEmail = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (linkedUser != null && !linkedUser.getEmail().equalsIgnoreCase(email)) {
            if (userByEmail != null && !userByEmail.getId().equals(linkedUser.getId())) {
                throw emailBelongsToAnotherAccount(email);
            }
            throw new ConflictException("This employee is already linked to a different email account");
        }

        User user = linkedUser != null ? linkedUser : userByEmail;
        if (user != null && linkedUser == null) {
            Employee owner = employeeRepository.findByUser_IdAndDeletedFalse(user.getId()).orElse(null);
            if (owner == null || !owner.getId().equals(employee.getId())) {
                throw emailBelongsToAnotherAccount(email);
            }
            employee.setUser(user);
        }

        if (user != null && "ACTIVE".equals(user.getAccountStatus()) && user.isActive() && !user.isMustChangePassword()) {
            throw new ConflictException("This employee account is already activated");
        }
        if (user == null) {
            user = new User();
            user.setUsername(uniqueUsername(employee.getEmployeeCode()));
            user.setEmail(email);
            user.setFullName(employee.getFullName());
            Role role = roleRepository.findByName("EMPLOYEE")
                    .orElseThrow(() -> new BadRequestException("Employee role is not configured"));
            user.setRoles(Set.of(role));
            user.setCompany(employee.getCompany());
        }
        user.setPasswordHash(passwordEncoder.encode(newToken()));
        user.setActive(false);
        user.setMustChangePassword(true);
        user.setAccountStatus("INVITED");
        User saved = userRepository.save(user);
        employee.setUser(saved);
        employeeRepository.save(employee);
        return saved;
    }

    private ConflictException emailBelongsToAnotherAccount(String email) {
        return new ConflictException("Email '" + email + "' is already associated with another account");
    }

    private Employee findEmployee(Long employeeId) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
    }

    private String normalizeAndValidateEmail(Employee employee) {
        if (employee.getEmail() == null) {
            throw new BadRequestException("Employee must have a valid email address");
        }
        String normalized = employee.getEmail().trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("Employee must have a valid email address");
        }
        if (!normalized.equals(employee.getEmail())) {
            employee.setEmail(normalized);
            employeeRepository.save(employee);
        }
        return normalized;
    }

    private String uniqueUsername(String employeeCode) {
        String base = employeeCode.toLowerCase();
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) username = base + (++suffix);
        return username;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}