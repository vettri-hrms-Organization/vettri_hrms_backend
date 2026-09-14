package com.haodaone.employee.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmploymentStatusTest {

    @Test
    void normalizeHandlesLegacyAndMixedCaseValues() {
        assertEquals(EmploymentStatus.ACTIVE, EmploymentStatus.normalize("ACTIVE"));
        assertEquals(EmploymentStatus.ACTIVE, EmploymentStatus.normalize("active"));
        assertEquals(EmploymentStatus.ON_LEAVE, EmploymentStatus.normalize("ON_LEAVE"));
        assertEquals(EmploymentStatus.ON_LEAVE, EmploymentStatus.normalize("On Leave"));
        assertEquals(EmploymentStatus.NOTICE_PERIOD, EmploymentStatus.normalize("notice_period"));
        assertEquals(EmploymentStatus.RESIGNED, EmploymentStatus.normalize(" resigned "));
        assertEquals(EmploymentStatus.TERMINATED, EmploymentStatus.normalize("terminated"));
    }
}
