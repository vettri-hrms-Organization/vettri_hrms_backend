package com.haodaone.demo.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request / response shapes for the SUPER_ADMIN demo workspace endpoints (/api/admin/demo-companies). */
public final class DemoCompanyDtos {

    private DemoCompanyDtos() {
    }

    /** Optional display name; a default is used when blank. */
    public record CreateRequest(@Size(max = 200) String name) {
    }

    /** One login that can be used in the demo workspace. All logins share the returned password. */
    public record DemoLogin(String role, String username, String fullName) {
    }

    /**
     * Returned once by create and reset. The password is never stored in plain text and cannot be
     * retrieved again - a reset generates a new one.
     */
    public record DemoCredentials(Long companyId, String companyName, String username, String password,
                                  List<DemoLogin> logins, Map<String, Long> counts) {
    }

    /** updatedAt moves on every reset (the company row is touched when it is re-seeded). */
    public record DemoCompanySummary(Long id, String name, LocalDateTime createdAt, LocalDateTime updatedAt,
                                     long employeeCount, String adminUsername) {
    }
}
