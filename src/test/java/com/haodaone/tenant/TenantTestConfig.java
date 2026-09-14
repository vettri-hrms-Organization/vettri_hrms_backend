package com.haodaone.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.TestPropertySource;

@Configuration
@Profile("test")
@TestPropertySource("classpath:application-test.properties")
public class TenantTestConfig {
}
