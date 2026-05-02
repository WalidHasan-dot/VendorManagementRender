package com.vms.repository;

import com.vms.entity.Invoice;
import com.vms.entity.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByPurchaseOrderId(UUID purchaseOrderId);

    long countByStatus(com.vms.entity.Status status);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.status = :status")
    Double sumAmountByStatus(com.vms.entity.Status status);

    @org.springframework.data.jpa.repository.Query("SELECT EXTRACT(MONTH FROM i.issueDate) as month, SUM(i.amount) as revenue FROM Invoice i WHERE i.status = 'PAID' GROUP BY month")
    java.util.List<Object[]> getMonthlyRevenue();

    long countByPurchaseOrder_VendorId(UUID vendorId);

    long countByStatusAndPurchaseOrder_VendorId(Status status, UUID vendorId);

    java.util.List<Invoice> findByPurchaseOrder_VendorId(UUID vendorId);

    Page<Invoice> findByStatus(Status status, Pageable pageable);
}
