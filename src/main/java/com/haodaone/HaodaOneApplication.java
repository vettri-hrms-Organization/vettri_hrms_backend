package com.haodaone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HaodaOne - Enterprise HRMS.
 *
 * Phase 0 (this codebase): the foundation every later module builds on -
 * JWT authentication, role/permission-based access control, audit logging,
 * and the base entity conventions (soft delete, optimistic versioning,
 * audit columns) used across the whole platform.
 *
 * Phase 1+ (Employee Management, Attendance, Leave, Recruitment,
 * Performance, etc.) get added as their own packages alongside `user`,
 * `auth`, and `audit` below - each depending on this foundation rather
 * than reinventing security/audit per module.
 */
@SpringBootApplication
public class HaodaOneApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaodaOneApplication.class, args);
    }
}
