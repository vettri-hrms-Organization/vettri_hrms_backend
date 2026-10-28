package com.haodaone.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.auth.dto.MfaDTO;
import com.haodaone.auth.entity.TwoFactorPolicy;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end two-step login on H2 (TOTP, email OTP, recovery codes, trusted device). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MfaLoginFlowTest {

    private static final String PASSWORD = "CorrectPass1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MfaService mfaService;
    @MockBean private EmailService emailService;

    /** A fixed instant 5 seconds into a TOTP step, so +30s is always exactly the next step. */
    private final Instant base = Instant.ofEpochSecond((Instant.now().getEpochSecond() / 30) * 30 + 5);

    @AfterEach
    void restoreClock() {
        mfaService.setClock(Clock.systemDefaultZone());
    }

    @Test
    void loginWithoutTwoFactorKeepsTheNormalResponseShape() throws Exception {
        User user = createCommittedUser("mfa-none");
        JsonNode response = login(user.getUsername(), null, 200);
        assertFalse(response.path("accessToken").asText().isBlank());
        assertTrue(response.path("mfaRequired").isMissingNode());
        assertTrue(response.path("mfaToken").isMissingNode());
        assertEquals("Bearer", response.path("tokenType").asText());
    }

    @Test
    void totpLoginIsTwoStepRejectsReplayAndSupportsRecoveryCodesAndTrustedDevices() throws Exception {
        User user = createCommittedUser("mfa-totp");
        clockAt(0);
        MfaDTO.TotpEnrollResponse enroll = mfaService.enrollTotp(user.getId());
        assertTrue(enroll.otpauthUri().startsWith("otpauth://totp/Vettri%20HRMS:"));
        byte[] secret = Totp.base32Decode(enroll.secret());
        long step0 = Totp.timeStep(base.getEpochSecond());
        MfaDTO.EnableResponse enabled = mfaService.confirmTotp(user.getId(), Totp.generateCode(secret, step0));
        assertEquals(10, enabled.recoveryCodes().size());
        assertTrue(enabled.status().totpEnabled());

        clockAt(30);
        JsonNode challenge = login(user.getUsername(), null, 200);
        assertTrue(challenge.path("mfaRequired").asBoolean());
        assertTrue(challenge.path("accessToken").isNull() || challenge.path("accessToken").isMissingNode());
        List<String> methods = texts(challenge.path("methods"));
        assertEquals(List.of("TOTP", "RECOVERY"), methods);
        String mfaToken = challenge.path("mfaToken").asText();

        // The code used to confirm enrolment is a replay now: rejected and counted as a failed attempt.
        verify(mfaToken, "TOTP", Totp.generateCode(secret, step0), false, 401);
        assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts());

        String freshCode = Totp.generateCode(secret, step0 + 1);
        JsonNode session = verify(mfaToken, "TOTP", freshCode, false, 200);
        assertFalse(session.path("accessToken").asText().isBlank());
        assertFalse(session.path("refreshToken").asText().isBlank());
        assertTrue(session.path("user").path("mfaEnabled").asBoolean());
        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts());

        // The challenge is single use, and the same TOTP code can't be replayed on a new challenge.
        verify(mfaToken, "TOTP", freshCode, false, 401);
        String secondToken = login(user.getUsername(), null, 200).path("mfaToken").asText();
        verify(secondToken, "TOTP", freshCode, false, 401);

        // Recovery codes work once.
        String recovery = enabled.recoveryCodes().get(0);
        verify(secondToken, "RECOVERY", recovery.toUpperCase(), false, 200);
        String thirdToken = login(user.getUsername(), null, 200).path("mfaToken").asText();
        verify(thirdToken, "RECOVERY", recovery, false, 401);

        // Remember this device: the returned token skips the second factor next time.
        clockAt(60);
        JsonNode remembered = verify(thirdToken, "TOTP", Totp.generateCode(secret, step0 + 2), true, 200);
        String deviceToken = remembered.path("deviceToken").asText();
        assertFalse(deviceToken.isBlank());
        JsonNode direct = login(user.getUsername(), deviceToken, 200);
        assertTrue(direct.path("mfaRequired").isMissingNode());
        assertFalse(direct.path("accessToken").asText().isBlank());
        assertTrue(login(user.getUsername(), "not-a-device", 200).path("mfaRequired").asBoolean());
    }

    @Test
    void enforcedPolicyChallengesUnenrolledUsersByEmailAndEnforcesOtpLimits() throws Exception {
        User user = createCommittedUser("mfa-email");
        mfaService.updatePolicy(user.getCompany().getId(), TwoFactorPolicy.REQUIRED_ALL);
        AtomicReference<String> lastCode = new AtomicReference<>();
        doAnswer(invocation -> {
            lastCode.set(invocation.getArgument(2));
            return true;
        }).when(emailService).sendMfaCodeEmail(anyString(), anyString(), anyString(), anyInt());

        clockAt(0);
        JsonNode challenge = login(user.getUsername(), null, 200);
        assertEquals(List.of("EMAIL"), texts(challenge.path("methods")));
        String mfaToken = challenge.path("mfaToken").asText();

        JsonNode sent = postJson("/api/auth/mfa/email/send", Map.of("mfaToken", mfaToken), 200);
        assertTrue(sent.path("sentTo").asText().contains("***@example.com"));
        String code = lastCode.get();
        assertNotNull(code);
        postJson("/api/auth/mfa/email/send", Map.of("mfaToken", mfaToken), 400); // resend throttle

        verify(mfaToken, "EMAIL", wrong(code), false, 401);
        JsonNode session = verify(mfaToken, "EMAIL", code, false, 200);
        assertTrue(session.path("user").path("mfaEnrollmentRequired").asBoolean());
        verify(mfaToken, "EMAIL", code, false, 401);

        // Self-service email setup: max 5 attempts per code ...
        clockAt(120);
        mfaService.sendSetupEmailCode(user.getId());
        String setupCode = lastCode.get();
        for (int i = 0; i < 5; i++) {
            assertThrows(BadRequestException.class, () -> mfaService.enableEmailOtp(user.getId(), wrong(setupCode)));
        }
        assertThrows(BadRequestException.class, () -> mfaService.enableEmailOtp(user.getId(), setupCode));

        // ... a 5 minute expiry ...
        clockAt(200);
        mfaService.sendSetupEmailCode(user.getId());
        String expiring = lastCode.get();
        clockAt(200 + MfaService.EMAIL_OTP_TTL_SECONDS + 1);
        assertThrows(BadRequestException.class, () -> mfaService.enableEmailOtp(user.getId(), expiring));

        // ... and single use.
        clockAt(900);
        mfaService.sendSetupEmailCode(user.getId());
        String good = lastCode.get();
        MfaDTO.EnableResponse enabled = mfaService.enableEmailOtp(user.getId(), good);
        assertTrue(enabled.status().emailOtpEnabled());
        assertFalse(enabled.status().enrollmentRequired());
        assertEquals(10, enabled.recoveryCodes().size());
        assertThrows(BadRequestException.class, () -> mfaService.enableEmailOtp(user.getId(), good));

        assertEquals(List.of("EMAIL", "RECOVERY"), texts(login(user.getUsername(), null, 200).path("methods")));
        // Required by policy: the user cannot switch it off.
        assertThrows(BadRequestException.class,
                () -> mfaService.disable(user.getId(), PASSWORD, "RECOVERY", enabled.recoveryCodes().get(0)));
    }

    // ------------------------------------------------------------------ helpers

    private void clockAt(long secondsAfterBase) {
        mfaService.setClock(Clock.fixed(base.plusSeconds(secondsAfterBase), ZoneId.systemDefault()));
    }

    private static String wrong(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }

    private User createCommittedUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        Company company = new Company();
        company.setName(username);
        company = companyRepository.save(company);
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("MFA Test");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        user = userRepository.saveAndFlush(user);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        return user;
    }

    private JsonNode login(String username, String deviceToken, int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        if (deviceToken != null) {
            body.put("deviceToken", deviceToken);
        }
        return postJson("/api/auth/login", body, expectedStatus);
    }

    private JsonNode verify(String mfaToken, String method, String code, boolean remember, int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("mfaToken", mfaToken);
        body.put("method", method);
        body.put("code", code);
        body.put("rememberDevice", remember);
        return postJson("/api/auth/mfa/verify", body, expectedStatus);
    }

    private JsonNode postJson(String path, Map<String, ?> body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
