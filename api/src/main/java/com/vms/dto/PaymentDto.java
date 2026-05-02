package com.vms.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PaymentDto {
    private UUID id;
    private UUID invoiceId;
    private String invoiceNumber;
    private Double amount;
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private String method;           // ← Add this back
    private String referenceNumber;
    private String status;
    private String paymentStatus;    // ← Add this back too (safe)
    private String vendorName;
    private String purchaseOrderId;
}





//package com.vms.dto;
//
//import lombok.Data;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Data
//public class PaymentDto {
//    private UUID id;
//    private UUID invoiceId;
//    private Double amount;
//    private LocalDateTime paymentDate;
//    private String method;
//    private String vendorName;
//    private String purchaseOrderId;
//    private String paymentStatus;
//}
