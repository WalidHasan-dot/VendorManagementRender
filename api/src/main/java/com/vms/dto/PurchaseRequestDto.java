package com.vms.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalDate;

@Data
public class PurchaseRequestDto {
    private UUID id;
    private UUID userId;
    private String userName;
    private String productName;
    private Integer quantity;
    private LocalDate requiredDate;
    private String purpose;
    private String status;
    private LocalDateTime createdAt;
}
