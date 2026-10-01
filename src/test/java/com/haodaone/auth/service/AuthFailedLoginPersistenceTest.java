package com.haodaone.auth.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFailedLoginPersistenceTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void failedAttemptsPersistAndLockAccountAtConfiguredThreshold() throws Exception {
        String username = "failed-login-" + UUID.randomUUID();
        Company company = new Company();
        company.setName(username);
        company = companyRepository.save(company);
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Failed Login Test");
        user.setPasswordHash(passwordEncoder.encode("CorrectPass1!"));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        userRepository.saveAndFlush(user);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        String body = "{\"username\":\"" + username + "\",\"password\":\"wrong\"}";
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        User persisted = userRepository.findByUsernameAndDeletedFalse(username).orElseThrow();
        assertEquals(5, persisted.getFailedLoginAttempts());
        assertNotNull(persisted.getLockedUntil());
    }
}