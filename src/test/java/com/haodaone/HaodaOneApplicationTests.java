package com.haodaone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Standard Spring Boot smoke test - confirms the application context loads
 * (security config, JPA auditing, all beans wire up correctly). Requires a
 * reachable Postgres instance (see docker-compose.yml).
 */
@SpringBootTest
class HaodaOneApplicationTests {

    @Test
    void contextLoads() {
    }
}
