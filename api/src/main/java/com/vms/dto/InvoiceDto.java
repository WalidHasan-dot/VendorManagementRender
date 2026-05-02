package com.vms.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class InvoiceDto {
    private UUID id;
    private UUID purchaseOrderId;
    private String invoiceNumber;
    private String poNumber;
    private String vendorName;
    private String vendorEmail;
    private Double amount;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private java.time.LocalDate issueDate;
    
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private java.time.LocalDate requiredDate;
    private Integer totalQuantity;
    private com.vms.entity.Status status;
    private String descriptionSummary;
    private String rejectionNote;
    private List<PurchaseOrderItemDto> items;
    private UUID paymentId;
}
