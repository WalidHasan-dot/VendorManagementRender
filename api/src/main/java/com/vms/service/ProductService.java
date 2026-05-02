package com.vms.service;

import com.vms.dto.PageResponse;
import com.vms.dto.ProductDto;
import com.vms.entity.Product;
import com.vms.entity.Vendor;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.ProductRepository;
import com.vms.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private com.vms.repository.PurchaseOrderItemRepository purchaseOrderItemRepository;

    public List<ProductDto> getAllProducts() {
        if (com.vms.security.SecurityUtils.hasRole("ROLE_ADMIN")) {
            return productRepository.findAll().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        }

        if (com.vms.security.SecurityUtils.hasRole("ROLE_VENDOR")) {
            com.vms.security.UserDetailsImpl userDetails = com.vms.security.SecurityUtils.getCurrentUser().orElseThrow(() -> new RuntimeException("Not authenticated"));
            if (userDetails.getVendorId() != null) {
                return productRepository.findByVendorId(userDetails.getVendorId()).stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList());
            }
        }
        
        // Default to returning all (or maybe empty?) for other roles?
        // Let's stick with all for now for safety, but vendors are now filtered.
        return productRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public PageResponse<ProductDto> getAllProductsPaginated(String name, Pageable pageable) {
        if (com.vms.security.SecurityUtils.hasRole("ROLE_ADMIN")) {
            Page<Product> page = name != null && !name.isBlank()
                    ? productRepository.findByNameContainingIgnoreCase(name.trim(), pageable)
                    : productRepository.findAll(pageable);
            List<ProductDto> content = page.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
            return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
        }

        if (com.vms.security.SecurityUtils.hasRole("ROLE_VENDOR")) {
            com.vms.security.UserDetailsImpl userDetails = com.vms.security.SecurityUtils.getCurrentUser().orElseThrow(() -> new RuntimeException("Not authenticated"));
            if (userDetails.getVendorId() != null) {
                Page<Product> page = name != null && !name.isBlank()
                        ? productRepository.findByNameContainingIgnoreCaseAndVendorId(name.trim(), userDetails.getVendorId(), pageable)
                        : productRepository.findByVendorId(userDetails.getVendorId(), pageable);
                List<ProductDto> content = page.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
                return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
            }
        }

        Page<Product> page = name != null && !name.isBlank()
                ? productRepository.findByNameContainingIgnoreCase(name.trim(), pageable)
                : productRepository.findAll(pageable);
        List<ProductDto> content = page.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public ProductDto getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return convertToDto(product);
    }

    public ProductDto createProduct(ProductDto productDto) {
        Product product = new Product();
        product.setName(productDto.getName());
        product.setDescription(productDto.getDescription());
        product.setPrice(productDto.getPrice());

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Not authenticated");
        }
        
        Vendor vendor = null;
        if (com.vms.security.SecurityUtils.hasRole("ROLE_VENDOR")) {
            com.vms.security.UserDetailsImpl userDetails = com.vms.security.SecurityUtils.getCurrentUser().orElseThrow(() -> new RuntimeException("Not authenticated"));
            if (userDetails.getVendorId() == null) {
                throw new RuntimeException("Vendor ID not found in JWT");
            }
            vendor = vendorRepository.findById(userDetails.getVendorId())
                    .orElseThrow(() -> new RuntimeException("Vendor not found"));
        } else {
            if (productDto.getVendorId() != null) {
                vendor = vendorRepository.findById(productDto.getVendorId())
                        .orElseThrow(() -> new RuntimeException("Vendor not found"));
            } else {
                throw new RuntimeException("Vendor ID is required for ADMIN to create product.");
            }
        }

        product.setVendor(vendor);

        return convertToDto(productRepository.save(product));
    }

    public ProductDto updateProduct(UUID id, ProductDto productDto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if (com.vms.security.SecurityUtils.hasRole("ROLE_VENDOR")) {
            com.vms.security.UserDetailsImpl userDetails = com.vms.security.SecurityUtils.getCurrentUser().orElseThrow(() -> new RuntimeException("Not authenticated"));
            if (product.getVendor() == null || !product.getVendor().getId().equals(userDetails.getVendorId())) {
                throw new RuntimeException("Not authorized to update this product");
            }
        }
        updateEntity(product, productDto);
        return convertToDto(productRepository.save(product));
    }

    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
                
        if (purchaseOrderItemRepository.existsByProductId(id)) {
            throw new RuntimeException("Cannot delete product: It is already linked to existing purchase orders.");
        }

        if (com.vms.security.SecurityUtils.hasRole("ROLE_VENDOR")) {
            com.vms.security.UserDetailsImpl userDetails = com.vms.security.SecurityUtils.getCurrentUser().orElseThrow(() -> new RuntimeException("Not authenticated"));
            if (product.getVendor() == null || !product.getVendor().getId().equals(userDetails.getVendorId())) {
                throw new RuntimeException("Not authorized to delete this product");
            }
        }
        productRepository.deleteById(id);
    }

    public List<ProductDto> getProductsByVendor(UUID vendorId) {
    return productRepository.findByVendorId(vendorId)
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
}

    private ProductDto convertToDto(Product product) {
        if (product == null)
            return null;
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        if (product.getVendor() != null) {
            dto.setVendorId(product.getVendor().getId());
            dto.setVendorName(product.getVendor().getCompanyName());
        }
        return dto;
    }

    private void updateEntity(Product product, ProductDto dto) {
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        if (dto.getVendorId() != null) {
            Vendor vendor = vendorRepository.findById(dto.getVendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + dto.getVendorId()));
            product.setVendor(vendor);
        }
    }
}
