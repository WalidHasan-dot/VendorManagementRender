package com.vms.repository;

import com.vms.entity.PurchaseRequest;
import com.vms.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {
    List<PurchaseRequest> findByUserId(UUID userId);
    List<PurchaseRequest> findByStatus(Status status);
    long countByUserId(UUID userId);
    long countByUserIdAndStatus(UUID userId, Status status);
}
