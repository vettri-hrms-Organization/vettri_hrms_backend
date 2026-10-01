package com.haodaone.auth.service;

import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailService emailService;

    @Test
    void resetLinkCanBeUsedOnceAndNewPasswordAuthenticates() throws Exception {
        String username = "reset-" + UUID.randomUUID();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Password Reset Test");
        user.setPasswordHash(passwordEncoder.encode("OldPass1!"));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        user.setRoles(Set.of(employeeRole));
        userRepository.saveAndFlush(user);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        AtomicReference<String> rawToken = new AtomicReference<>();
        doAnswer(invocation -> {
            rawToken.set(invocation.getArgument(2));
            return null;
        }).when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If an account matches that identifier, a reset link will be sent."));
        assertNotNull(rawToken.get());

        String resetBody = "{\"token\":\"" + rawToken.get() + "\",\"password\":\"NewPass2!\"}";
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"NewPass2!\"}"))
                .andExpect(status().isOk());
    }
}
