package com.vms.entity;

public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING,
    APPROVED,
    REJECTED,
    DENIED,     // User registration rejected (same semantics as REJECTED for users)
    COMPLETED,
    CANCELLED,
    PAID,
    OVERDUE,
    DISABLED,
    DRAFT,              // Purchase order initial state
    SUBMITTED,          // Purchase order submitted for approval (alias: REQUESTED)
    REQUESTED,          // Purchase request submitted
    VENDOR_ASSIGNED,    // Vendor assigned to PO
    VENDOR_ACCEPTED,    // Vendor accepted the order
    DELIVERED,          // Order delivered
    INVOICED,            // Invoice submitted for order
    INVOICE_SUBMITTED

}

