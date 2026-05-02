package com.vms.dto;

import com.vms.entity.*;
import java.util.stream.Collectors;

public class EntityMapper {

    public static PurchaseOrderDto mapToPurchaseOrderDto(PurchaseOrder po) {
        if (po == null) return null;
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setId(po.getId());
        if (po.getVendor() != null) {
            dto.setVendorId(po.getVendor().getId());
            dto.setVendorName(po.getVendor().getCompanyName());
        }
        if (po.getCreatedBy() != null) {
            dto.setCreatedById(po.getCreatedBy().getId());
            dto.setCreatedByUsername(po.getCreatedBy().getUsername());
        }
        dto.setTotalAmount(po.getTotalAmount());
        dto.setCreatedAt(po.getCreatedAt());
        if (po.getStatus() != null) {
            dto.setStatus(po.getStatus().name());
        }
        dto.setDeliveryType(po.getDeliveryType());
        dto.setRequiredDate(po.getRequiredDate());
        dto.setDeliveryUserId(po.getDeliveryUserId());
        dto.setDeliveryAddress(po.getDeliveryAddress());
        if (po.getItems() != null) {
            dto.setTotalQuantity(po.getItems().stream().mapToInt(PurchaseOrderItem::getQuantity).sum());
        }
        return dto;
    }

    public static PaymentDto mapToPaymentDto(Payment payment) {
        if (payment == null) return null;
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        if (payment.getInvoice() != null) {
            dto.setInvoiceId(payment.getInvoice().getId());
            dto.setInvoiceNumber(payment.getInvoice().getInvoiceNumber());
        }
        dto.setAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setMethod(payment.getMethod());                    // ← use getMethod()
        dto.setReferenceNumber(payment.getReferenceNumber());  // ← now exists
        dto.setStatus(payment.getStatus());                    // ← now exists
        return dto;
    }

    public static UserDto mapToUserDto(User user) {
        if (user == null) return null;
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        if (user.getStatus() != null) {
            dto.setStatus(user.getStatus().name());
        }
        dto.setUserRole(user.getUserRole());
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet()));
        }
        return dto;
    }

    public static AuditLogDto mapToAuditLogDto(AuditLog log) {
        if (log == null) return null;
        AuditLogDto dto = new AuditLogDto();
        dto.setId(log.getId());
        if (log.getUser() != null) {
            dto.setUsername(log.getUser().getUsername());
        }
        dto.setAction(log.getAction());
        dto.setEntity(log.getEntity());
        dto.setEntityId(log.getEntityId());
        dto.setTimestamp(log.getTimestamp());
        dto.setDetails(log.getDetails());
        return dto;
    }
}
