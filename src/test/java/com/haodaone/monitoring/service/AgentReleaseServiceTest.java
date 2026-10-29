package com.haodaone.monitoring.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.monitoring.dto.AgentReleaseDTO;
import com.haodaone.monitoring.repository.AgentReleaseRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentReleaseServiceTest {

    @Test
    void comparesDottedVersionsNumerically() {
        assertTrue(AgentReleaseService.compareVersions("2.10.0", "2.9.9") > 0);
        assertEquals(0, AgentReleaseService.compareVersions("2.1", "2.1.0.0"));
        assertTrue(AgentReleaseService.compareVersions("2.1.0", "2.1.0.1") < 0);
        assertTrue(AgentReleaseService.compareVersions("2.1.0", null) > 0);
    }

    @Test
    void refusesNonHttpsDownloads() {
        AgentReleaseService service = new AgentReleaseService(mock(AgentReleaseRepository.class), mock(AuditLogService.class));
        assertThrows(BadRequestException.class, () -> service.publish(new AgentReleaseDTO.PublishRequest(
                "2.2.0", "http://downloads.example.com/setup.exe", "a".repeat(64), null, true)));
        assertThrows(BadRequestException.class, () -> service.publish(new AgentReleaseDTO.PublishRequest(
                "2.2.0", "https://user:pass@downloads.example.com/setup.exe", "a".repeat(64), null, true)));
    }
}
