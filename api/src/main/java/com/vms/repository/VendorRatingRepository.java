package com.vms.repository;

import com.vms.entity.VendorRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorRatingRepository extends JpaRepository<VendorRating, UUID> {
    List<VendorRating> findByVendorId(UUID vendorId);

    boolean existsByPurchaseOrderIdAndUserId(UUID purchaseOrderId, UUID userId);
}

