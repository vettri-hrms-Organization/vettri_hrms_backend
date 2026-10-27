package com.haodaone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ApplicationTimeConfig {
    public static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }
}
