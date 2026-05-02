package com.vms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatusTimelineEntryDto {
    private String action;
    private String performedBy;
    private String details;
    private LocalDateTime timestamp;
}

