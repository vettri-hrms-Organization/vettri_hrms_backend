package com.haodaone.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class ApplicationTimeConfig {
    public static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Kolkata");

    @PostConstruct
    void configureDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_ZONE));
    }

    @Bean
    public Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }
}
