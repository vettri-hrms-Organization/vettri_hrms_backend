package com.haodaone.audit.dto;

import com.haodaone.audit.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogDTO(Long id, String entityName, Long entityId, String action,
                          String performedBy, LocalDateTime performedAt, String ipAddress, String details) {
    public static AuditLogDTO from(AuditLog log) {
        return new AuditLogDTO(log.getId(), log.getEntityName(), log.getEntityId(), log.getAction(),
                log.getPerformedBy(), log.getPerformedAt(), log.getIpAddress(), log.getDetails());
    }
}