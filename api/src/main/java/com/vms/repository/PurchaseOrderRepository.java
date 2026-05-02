package com.vms.repository;

import com.vms.entity.PurchaseOrder;
import com.vms.entity.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findByVendorId(UUID vendorId);

    List<PurchaseOrder> findByCreatedById(UUID userId);

    long countByVendorId(UUID vendorId);

    long countByCreatedById(UUID userId);

    Page<PurchaseOrder> findByStatus(Status status, Pageable pageable);

    long countByStatus(Status status);

    Page<PurchaseOrder> findByCreatedById(UUID userId, Pageable pageable);

    Page<PurchaseOrder> findByVendorId(UUID vendorId, Pageable pageable);
    
    List<PurchaseOrder> findByCreatedByIdAndStatusIn(UUID userId, List<Status> statuses);

    long countByCreatedByIdAndStatus(UUID userId, Status status);
}
