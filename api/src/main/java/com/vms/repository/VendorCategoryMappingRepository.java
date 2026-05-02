package com.vms.repository;

import com.vms.entity.VendorCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorCategoryMappingRepository extends JpaRepository<VendorCategoryMapping, Long> {
    List<VendorCategoryMapping> findByVendorId(java.util.UUID vendorId);
}
