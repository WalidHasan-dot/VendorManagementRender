package com.vms.dto;

import lombok.Data;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class PurchaseRequestApprovalDto {
    private UUID vendorId;
    private String productName;
    private Integer quantity;
    private Double price;
    private LocalDateTime deliveryDate;
    private String notes;
    private String deliveryType;
    private UUID deliveryUserId;
}
