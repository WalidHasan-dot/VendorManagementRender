package com.vms.repository;

import com.vms.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    boolean existsByEmail(String email);

    long countByStatus(com.vms.entity.Status status);

    java.util.List<Vendor> findByStatus(com.vms.entity.Status status);

    Optional<Vendor> findByUser(com.vms.entity.User user);

    Optional<Vendor> findByUserId(UUID userId);

    Page<Vendor> findByCompanyNameContainingIgnoreCase(String companyName, Pageable pageable);

    Optional<Vendor> findByUserUsername(String username);
}
