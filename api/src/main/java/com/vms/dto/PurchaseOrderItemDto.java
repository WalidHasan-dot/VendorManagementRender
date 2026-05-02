package com.vms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PurchaseOrderItemDto {
    private Long id;
    private UUID productId;
    private String productName;
    private String productDescription;
    private Integer quantity;
    private Double price;
}
