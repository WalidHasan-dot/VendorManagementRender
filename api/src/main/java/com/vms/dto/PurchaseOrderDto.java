package com.vms.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseOrderDto {
    private UUID id;
    private UUID vendorId;
    private String vendorName;
    private UUID createdById;
    private String createdByUsername;
    private Double totalAmount;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    private String status;
    private List<PurchaseOrderItemDto> items;
    private String deliveryType;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private java.time.LocalDate requiredDate;
    
    private Integer totalQuantity;
    private UUID deliveryUserId;
    private String deliveryAddress;
}
