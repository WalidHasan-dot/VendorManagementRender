package com.vms.repository;

import com.vms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserId(UUID userId);

    List<AuditLog> findTop10ByOrderByTimestampDesc();

    List<AuditLog> findByEntityAndEntityIdOrderByTimestampDesc(String entity, String entityId);
}
