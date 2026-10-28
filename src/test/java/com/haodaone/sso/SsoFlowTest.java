package com.haodaone.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.sso.entity.CompanySsoSetting;
import com.haodaone.sso.repository.CompanySsoSettingRepository;
import com.haodaone.sso.service.OidcHttpClient;
import com.haodaone.sso.service.SsoService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SSO-only enforcement on password login and the full start -> callback -> exchange flow with a stubbed IdP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.sso.google.client-id=test-google-client",
        "app.sso.google.client-secret=test-secret",
        "app.sso.redirect-base-url=http://localhost",
        "app.sso.frontend-url=http://app.test"
})
class SsoFlowTest {

    private static final String PASSWORD = "CorrectPass1!";
    private static KeyPair keyPair;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanySsoSettingRepository ssoSettings;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailService emailService;
    @MockBean private OidcHttpClient oidcHttpClient;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void ssoOnlyBlocksPasswordLoginExceptForTheBreakGlassAdmin() throws Exception {
        Company company = company("sso-only");
        User employee = user(company, "EMPLOYEE");
        User admin = user(company, "COMPANY_ADMIN");
        User otherAdmin = user(company, "COMPANY_ADMIN");

        // Before the policy: everyone signs in with a password.
        login(employee.getUsername(), 200);

        CompanySsoSetting setting = new CompanySsoSetting();
        setting.setCompany(company);
        setting.setSsoOnly(true);
        setting = ssoSettings.save(setting);

        JsonNode blocked = login(employee.getUsername(), 401);
        assertTrue(blocked.toString().contains("Google or Microsoft"), blocked::toString);
        // No break-glass user chosen: every company admin keeps password access.
        login(admin.getUsername(), 200);
        login(otherAdmin.getUsername(), 200);

        setting.setBreakGlassUserId(admin.getId());
        ssoSettings.save(setting);
        login(admin.getUsername(), 200);
        login(otherAdmin.getUsername(), 401);
        // A wrong password still reads as a wrong password (the policy is never revealed).
        Map<String, Object> wrong = Map.of("username", employee.getUsername(), "password", "nope");
        JsonNode wrongResponse = postJson("/api/auth/login", wrong, 401);
        assertFalse(wrongResponse.toString().contains("Google"));
    }

    @Test
    void providersEndpointListsConfiguredProvidersOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/sso/providers")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, body.path("providers").size());
        assertEquals("google", body.path("providers").get(0).path("id").asText());
    }

    @Test
    void fullFlowIssuesTokensThroughAOneTimeCode() throws Exception {
        Company company = company("sso-flow");
        User employee = user(company, "EMPLOYEE");

        Start start = start();
        stubIdp(start.nonce(), employee.getEmail(), "google-sub-" + UUID.randomUUID());

        String location = callback(start, start.binding());
        assertTrue(location.startsWith("http://app.test/login?sso_code="), location);
        assertFalse(location.contains("accessToken"));
        String code = URLDecoder.decode(location.substring(location.indexOf('=') + 1), StandardCharsets.UTF_8);

        JsonNode session = postJson("/api/auth/sso/exchange", Map.of("code", code), 200);
        assertFalse(session.path("accessToken").asText().isBlank());
        assertEquals(employee.getUsername(), session.path("user").path("username").asText());
        // Single use.
        postJson("/api/auth/sso/exchange", Map.of("code", code), 401);
    }

    @Test
    void callbackRejectsAMissingBrowserBindingAndUnknownEmails() throws Exception {
        Company company = company("sso-reject");
        user(company, "EMPLOYEE");

        Start stolen = start();
        stubIdp(stolen.nonce(), "nobody@example.com", "sub-x");
        assertTrue(callback(stolen, "wrong-binding").endsWith("sso_error=expired"));

        Start unknown = start();
        stubIdp(unknown.nonce(), "nobody-" + UUID.randomUUID() + "@example.com", "sub-" + UUID.randomUUID());
        assertTrue(callback(unknown, unknown.binding()).endsWith("sso_error=account_not_found"));

        // Replaying a used state fails too.
        assertTrue(callback(unknown, unknown.binding()).endsWith("sso_error=expired"));
    }

    // ------------------------------------------------------------------ helpers

    private record Start(String state, String nonce, String binding) {
    }

    private Start start() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/sso/google/start")).andExpect(status().isFound()).andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        Map<String, List<String>> params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertEquals("S256", params.get("code_challenge_method").get(0));
        String binding = null;
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(SsoService.BINDING_COOKIE + "=")) {
                binding = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
                assertTrue(header.contains("HttpOnly"));
            }
        }
        assertNotNull(binding);
        return new Start(decode(params.get("state").get(0)), decode(params.get("nonce").get(0)), binding);
    }

    private String callback(Start start, String binding) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/sso/google/callback")
                        .param("code", "auth-code")
                        .param("state", start.state())
                        .cookie(new Cookie(SsoService.BINDING_COOKIE, binding)))
                .andExpect(status().isFound()).andReturn();
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private void stubIdp(String nonce, String email, String subject) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        long now = Instant.now().getEpochSecond();
        claims.put("iss", "https://accounts.google.com");
        claims.put("aud", "test-google-client");
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", true);
        claims.put("nonce", nonce);
        claims.put("iat", now);
        claims.put("exp", now + 600);
        when(oidcHttpClient.exchangeCode(anyString(), anyMap())).thenReturn(Map.of("id_token", sign(claims)));
        when(oidcHttpClient.fetchJwks(anyString())).thenReturn(jwks());
    }

    private String sign(Map<String, Object> claims) throws Exception {
        String input = b64(objectMapper.writeValueAsBytes(Map.of("alg", "RS256", "kid", "test-k1")))
                + "." + b64(objectMapper.writeValueAsBytes(claims));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + b64(signer.sign());
    }

    private static Map<String, Object> jwks() {
        RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();
        return Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "test-k1", "use", "sig",
                "n", b64(unsigned(key.getModulus().toByteArray())),
                "e", b64(unsigned(key.getPublicExponent().toByteArray())))));
    }

    private Company company(String prefix) {
        Company company = new Company();
        company.setName(prefix + "-" + UUID.randomUUID());
        return companyRepository.save(company);
    }

    private User user(Company company, String role) {
        String username = "sso-" + role.toLowerCase() + "-" + UUID.randomUUID();
        Role roleEntity = roleRepository.findByName(role).orElseThrow();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("SSO Test");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setCompany(company);
        user.setRoles(Set.of(roleEntity));
        return userRepository.saveAndFlush(user);
    }

    private JsonNode login(String username, int expectedStatus) throws Exception {
        return postJson("/api/auth/login", Map.of("username", username, "password", PASSWORD), expectedStatus);
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

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
