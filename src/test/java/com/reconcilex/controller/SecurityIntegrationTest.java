package com.reconcilex.controller;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reconcilex.ReconcilexApplication;
import com.reconcilex.config.JwtProperties;
import com.reconcilex.entity.AppUser;
import com.reconcilex.entity.AppUserRole;
import com.reconcilex.entity.AuditEvent;
import com.reconcilex.repository.AppUserRepository;
import com.reconcilex.repository.AuditEventRepository;
import com.reconcilex.service.AuditEventService;
import com.reconcilex.service.JwtTokenService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconcilex_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void setupUsers() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_events CASCADE");
        jdbcTemplate.execute("DELETE FROM reconciliation_result_bank_rows");
        jdbcTemplate.execute("DELETE FROM reconciliation_result_internal_transactions");
        jdbcTemplate.execute("DELETE FROM reconciliation_results");
        jdbcTemplate.execute("DELETE FROM reconciliation_runs");
        jdbcTemplate.execute("DELETE FROM import_row_errors");
        jdbcTemplate.execute("DELETE FROM bank_statement_rows");
        jdbcTemplate.execute("DELETE FROM internal_transactions");
        jdbcTemplate.execute("DELETE FROM file_imports");
        userRepository.deleteAllInBatch();

        // Seed 1 ADMIN and 1 VIEWER
        AppUser admin = new AppUser("testadmin", passwordEncoder.encode("AdminPass12345!"), AppUserRole.ADMIN);
        userRepository.save(admin);

        AppUser viewer = new AppUser("testviewer", passwordEncoder.encode("ViewerPass12345!"), AppUserRole.VIEWER);
        userRepository.save(viewer);

        adminToken = jwtTokenService.generateToken("testadmin", AppUserRole.ADMIN);
        viewerToken = jwtTokenService.generateToken("testviewer", AppUserRole.VIEWER);
    }

    // 1. Login Endpoint & Generic 401 Hardening
    @Test
    void testLogin_success() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testadmin\",\"password\":\"AdminPass12345!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn", is(1800)))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    void testLogin_wrongPassword_returnsGeneric401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testadmin\",\"password\":\"WrongPassword12345!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid username or password")))
                .andExpect(jsonPath("$.path", is("/api/auth/login")));
    }

    @Test
    void testLogin_nonExistentUser_returnsIdenticalGeneric401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nonexistent_user\",\"password\":\"AnyPassword12345!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid username or password")))
                .andExpect(jsonPath("$.path", is("/api/auth/login")));
    }

    // 2. ADMIN can import, run, create users, read audit events
    @Test
    void testAdmin_canImportAndRun() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);

        MvcResult importResult = mockMvc.perform(multipart("/api/imports/bank")
                .file(bankFile)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        byte[] internalBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            internalBytes = is.readAllBytes();
        }
        MockMultipartFile internalFile = new MockMultipartFile("file", "internal.csv", "text/csv", internalBytes);

        MvcResult intResult = mockMvc.perform(multipart("/api/imports/internal")
                .file(internalFile)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String bankJson = importResult.getResponse().getContentAsString();
        Number bankImportId = com.jayway.jsonpath.JsonPath.parse(bankJson).read("$.importId");

        String intJson = intResult.getResponse().getContentAsString();
        Number internalImportId = com.jayway.jsonpath.JsonPath.parse(intJson).read("$.importId");

        // Run reconciliation
        mockMvc.perform(post("/api/reconciliation/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content(String.format("{\"bankImportId\":%d,\"internalImportId\":%d}", bankImportId.longValue(), internalImportId.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());

        // Create user
        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content("{\"username\":\"newadmin\",\"password\":\"SecurePassword123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("newadmin")))
                .andExpect(jsonPath("$.role", is("ADMIN")));

        // Read audit events
        mockMvc.perform(get("/api/audit-events")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // 3. VIEWER gets 403 on mutate/admin operations, but 200 on GET reads
    @Test
    void testViewer_forbiddenOnImportsAndRuns_allowedOnReads() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "dummy".getBytes());

        // 403 on bank import
        mockMvc.perform(multipart("/api/imports/bank")
                .file(file)
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Access denied")));

        // 403 on internal import
        mockMvc.perform(multipart("/api/imports/internal")
                .file(file)
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // 403 on post runs
        mockMvc.perform(post("/api/reconciliation/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + viewerToken)
                .content("{\"bankImportId\":1,\"internalImportId\":2}"))
                .andExpect(status().isForbidden());

        // 403 on user creation
        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + viewerToken)
                .content("{\"username\":\"someuser\",\"password\":\"ValidPassword123!\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());

        // 403 on audit events
        mockMvc.perform(get("/api/audit-events")
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 200 on GET runs (allowed for VIEWER)
        mockMvc.perform(get("/api/reconciliation/runs")
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    // 4. Missing Token gives 401
    @Test
    void testNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/reconciliation/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("Authentication is required")));
    }

    // 5. Expired and Tampered Tokens give 401
    @Test
    void testExpiredToken_returns401() throws Exception {
        // Generate token with expiration in the past (clock skew is explicitly configured to 0)
        String expiredToken = jwtTokenService.generateToken("testadmin", AppUserRole.ADMIN, -60L);

        mockMvc.perform(get("/api/reconciliation/runs")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void testTamperedToken_returns401() throws Exception {
        // Sign with a completely different secret key
        byte[] foreignKey = "completely-different-secret-key-32-bytes!".getBytes(StandardCharsets.UTF_8);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("reconcilex")
                .subject("testadmin")
                .claim("role", "ADMIN")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 1800000L))
                .build();
        SignedJWT tampered = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        tampered.sign(new MACSigner(foreignKey));
        String tamperedToken = tampered.serialize();

        mockMvc.perform(get("/api/reconciliation/runs")
                .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // 6. Audit actor equals the JWT username
    @Test
    void testAuditActor_equalsJwtUsername() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);

        mockMvc.perform(multipart("/api/imports/bank")
                .file(bankFile)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        List<AuditEvent> events = auditEventRepository.findByEventType(AuditEventService.IMPORT_COMPLETED);
        assertFalse(events.isEmpty(), "Expected IMPORT_COMPLETED event");
        assertEquals("testadmin", events.get(0).getActor(), "Audit actor must equal JWT username");
    }

    // 7. Fail-fast without JWT_SECRET
    @Test
    void testFailFast_withoutJwtSecret() {
        JwtProperties badProps = new JwtProperties();
        // secret is null/empty by default -> throws IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, badProps::validate);
        assertTrue(ex.getMessage().contains("JWT_SECRET environment variable is missing or blank"));
    }

    @Test
    void testFailFast_withShortJwtSecret() {
        // Secret shorter than 32 bytes
        org.springframework.test.util.ReflectionTestUtils.setField(
                jwtProperties, "secret", "short-secret-under-32-bytes"
        );
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, jwtProperties::validate);
            assertTrue(ex.getMessage().contains("at least 32 bytes"));
        } finally {
            // Restore valid secret
            org.springframework.test.util.ReflectionTestUtils.setField(
                    jwtProperties, "secret", "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256!"
            );
            jwtProperties.validate();
        }
    }

    // 8. Root path redirects to Swagger UI
    @Test
    void testRootPathRedirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"))
                .andExpect(header().string(HttpHeaders.LOCATION, "/swagger-ui/index.html"));
    }

    // 9. Enumerate every controller route and assert each non-public route returns 401 without token
    @Test
    void testAllNonPublicRoutesRequireAuthentication() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        Set<String> publicPaths = Set.of(
                "/",
                "/api/health",
                "/api/auth/login",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        );

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            if (mappingInfo.getPathPatternsCondition() == null) {
                continue;
            }

            for (PathPattern pattern : mappingInfo.getPathPatternsCondition().getPatterns()) {
                String patternString = pattern.getPatternString();
                if (publicPaths.contains(patternString) || patternString.startsWith("/swagger-ui") || patternString.startsWith("/v3/api-docs")) {
                    continue;
                }

                Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    methods = Set.of(RequestMethod.GET);
                }

                for (RequestMethod method : methods) {
                    // Substitute {id} or any other path variable with dummy "1"
                    String testPath = patternString.replaceAll("\\{[^}]+\\}", "1");

                    mockMvc.perform(request(HttpMethod.valueOf(method.name()), testPath))
                            .andExpect(status().isUnauthorized());
                }
            }
        }
    }

    // 9. No default user exists and app does not generate default security password
    @Test
    void testNoDefaultUserExists() {
        assertFalse(userRepository.existsByUsername("user"),
                "No default user 'user' must exist in the database");
    }

    /**
     * Verifies that BOTH login failure paths execute a real BCrypt comparison.
     *
     * <p>Strategy: call each path {@code REPS} times, sort the elapsed times, take the
     * median, and assert that neither median is less than a BCrypt lower-bound threshold
     * and that the two medians are within a reasonable ratio of each other.
     *
     * <p>Threshold: BCrypt with cost=10 takes at least 50 ms on any modern machine; if a
     * path returns faster than that it has short-circuited without doing real work.
     * Ratio bound: 3× gives ample room for GC pauses and JIT warm-up without allowing a
     * path that returns in ~1 ms (no BCrypt) to pass.
     */
    @Test
    void testTimingAttackHardening_bothPathsExecuteRealBcrypt() throws Exception {
        final int REPS = 7;
        final long BCRYPT_MIN_MS = 50L;   // BCrypt cost=10 must take at least this long
        final double RATIO_BOUND = 3.0;   // medians must be within this factor of each other

        long[] unknownUserTimes = new long[REPS];
        long[] wrongPasswordTimes = new long[REPS];

        for (int i = 0; i < REPS; i++) {
            long t0 = System.currentTimeMillis();
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nonexistent_" + i + "\",\"password\":\"AnyPassword12345!\"}"))
                    .andExpect(status().isUnauthorized());
            unknownUserTimes[i] = System.currentTimeMillis() - t0;
        }

        for (int i = 0; i < REPS; i++) {
            long t0 = System.currentTimeMillis();
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"testadmin\",\"password\":\"WrongPassword" + i + "!\"}"))
                    .andExpect(status().isUnauthorized());
            wrongPasswordTimes[i] = System.currentTimeMillis() - t0;
        }

        java.util.Arrays.sort(unknownUserTimes);
        java.util.Arrays.sort(wrongPasswordTimes);
        long medianUnknown  = unknownUserTimes[REPS / 2];
        long medianWrong    = wrongPasswordTimes[REPS / 2];

        System.out.printf("BCrypt timing test: unknown-user median=%d ms, wrong-password median=%d ms%n",
                medianUnknown, medianWrong);

        assertTrue(medianUnknown >= BCRYPT_MIN_MS,
                String.format("Unknown-user path median (%d ms) is below BCrypt lower bound (%d ms) "
                        + "— the dummy hash check may have been skipped", medianUnknown, BCRYPT_MIN_MS));

        assertTrue(medianWrong >= BCRYPT_MIN_MS,
                String.format("Wrong-password path median (%d ms) is below BCrypt lower bound (%d ms) "
                        + "— the password check may have been skipped", medianWrong, BCRYPT_MIN_MS));

        double ratio = (double) Math.max(medianUnknown, medianWrong)
                     / (double) Math.min(medianUnknown, medianWrong);

        assertTrue(ratio <= RATIO_BOUND,
                String.format("Timing ratio between paths (%.2fx) exceeds bound (%.1fx). "
                        + "unknown-user=%d ms, wrong-password=%d ms. "
                        + "One path may not be executing a real BCrypt comparison.",
                        ratio, RATIO_BOUND, medianUnknown, medianWrong));
    }
}
