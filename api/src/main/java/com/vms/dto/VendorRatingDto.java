package com.vms.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class VendorRatingDto {
    private UUID id;
    private UUID vendorId;
    private UUID purchaseOrderId;
    private int rating;
    private String comment;
    private String createdBy;
    private LocalDateTime createdAt;
}

