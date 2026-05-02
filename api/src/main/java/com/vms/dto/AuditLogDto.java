package com.vms.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogDto {
    private Long id;
    private String username;
    private String action;
    private String entity;
    private String entityId;
    private LocalDateTime timestamp;
    private String details;
}
