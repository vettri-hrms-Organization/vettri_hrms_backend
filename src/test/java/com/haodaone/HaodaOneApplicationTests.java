package com.haodaone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Standard Spring Boot smoke test - confirms the application context loads
 * (security config, JPA auditing, all beans wire up correctly). Requires a
 * reachable Postgres instance (see docker-compose.yml).
 */
@SpringBootTest
@ActiveProfiles("test")
class HaodaOneApplicationTests {

    @Test
    void contextLoads() {
    }
}
