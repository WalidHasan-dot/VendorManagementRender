package com.vms.repository;

import com.vms.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByInvoiceId(UUID invoiceId);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.amount) FROM Payment p")
    Double sumTotalRevenue();

    List<Payment> findByInvoice_PurchaseOrder_VendorId(UUID vendorId);
}
