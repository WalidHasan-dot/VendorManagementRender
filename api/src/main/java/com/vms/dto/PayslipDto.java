package com.vms.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.vms.dto.PurchaseOrderItemDto;

@Data
public class PayslipDto {
    private String vendorName;
    private UUID vendorId;
    private UUID invoiceId;
    private UUID purchaseOrderId;
    private Double paymentAmount;
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private String transactionReference;
    private String payslipReference;
    private List<PurchaseOrderItemDto> items;
}

