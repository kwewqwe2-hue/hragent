package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.AuditLog;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UserAccount actor, String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setTenantId(actor.getTenantId());
        log.setActorId(actor.getId());
        log.setActorName(actor.getName());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    public void logSystem(Long tenantId, String action, String targetType, Long targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setActorName("system");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    public List<AuditLog> latest(Long tenantId) {
        return auditLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
