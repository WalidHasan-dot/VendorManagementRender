package com.vms.repository;

import com.vms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    @EntityGraph(attributePaths = {"vendor"})
    List<Product> findAll();

    @EntityGraph(attributePaths = {"vendor"})
    List<Product> findByVendorId(UUID vendorId);

    long countByVendorId(UUID vendorId);

    @EntityGraph(attributePaths = {"vendor"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"vendor"})
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = {"vendor"})
    Page<Product> findByNameContainingIgnoreCaseAndVendorId(String name, UUID vendorId, Pageable pageable);

    @EntityGraph(attributePaths = {"vendor"})
    Page<Product> findByVendorId(UUID vendorId, Pageable pageable);

    @EntityGraph(attributePaths = {"vendor"})
    Optional<Product> findById(UUID id);

    Optional<Product> findByNameIgnoreCase(String name);
}
