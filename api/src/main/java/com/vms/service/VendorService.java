package com.vms.service;

import com.vms.dto.VendorDto;
import com.vms.entity.Product;
import com.vms.entity.PurchaseOrder;
import com.vms.entity.Vendor;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class VendorService {

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VendorCategoryRepository vendorCategoryRepository;

    @Autowired
    private VendorCategoryMappingRepository vendorCategoryMappingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public void registerVendor(com.vms.dto.VendorRegisterRequest request) {
        if (vendorRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use!");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already in use!");
        }

        com.vms.entity.User user = new com.vms.entity.User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(com.vms.entity.Status.DISABLED);
        user.setUserRole("VENDOR");

        com.vms.entity.Role vendorRole = roleRepository.findByName(com.vms.entity.RoleName.VENDOR)
                .orElseThrow(() -> new RuntimeException("Role VENDOR not found"));
        user.getRoles().add(vendorRole);

        userRepository.save(user);

        Vendor vendor = new Vendor();
        vendor.setCompanyName(request.getCompanyName());
        vendor.setContactPerson(request.getContactPerson());
        vendor.setEmail(request.getEmail());
        vendor.setPhone(request.getPhone());
        vendor.setAddress(request.getAddress());
        vendor.setStatus(com.vms.entity.Status.PENDING);
        vendor.setUser(user);

        vendorRepository.save(vendor);

        if (request.getCategoryId() != null) {
            com.vms.entity.VendorCategory category = vendorCategoryRepository.findById(request.getCategoryId())
                    .orElse(null);
            if (category != null) {
                com.vms.entity.VendorCategoryMapping mapping = new com.vms.entity.VendorCategoryMapping();
                mapping.setVendor(vendor);
                mapping.setCategory(category);
                vendorCategoryMappingRepository.save(mapping);
            }
        }

        notificationService.notifyAdmins("New vendor registration pending approval: " + vendor.getCompanyName());
        auditService.log(null, "VENDOR_REGISTRATION", "VENDOR", vendor.getId().toString(), "New vendor registered: " + vendor.getCompanyName());
    }

    public void approveVendor(UUID id, com.vms.entity.User admin) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        vendor.setStatus(com.vms.entity.Status.APPROVED);
        com.vms.entity.User user = vendor.getUser();
        if (user != null) {
            user.setStatus(com.vms.entity.Status.ACTIVE);
            userRepository.save(user);
        }
        vendorRepository.save(vendor);

        notificationService.createNotification(user, "Your account has been approved");
        auditService.log(admin, "APPROVE_VENDOR", "VENDOR", vendor.getId().toString(), "Vendor approved: " + vendor.getCompanyName());
    }

    public void rejectVendor(UUID id, com.vms.entity.User admin) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        vendor.setStatus(com.vms.entity.Status.REJECTED);
        com.vms.entity.User user = vendor.getUser();
        if (user != null) {
            user.setStatus(com.vms.entity.Status.DISABLED);
            userRepository.save(user);
        }
        vendorRepository.save(vendor);

        notificationService.createNotification(user, "Your account has been rejected");
        auditService.log(admin, "REJECT_VENDOR", "VENDOR", vendor.getId().toString(), "Vendor rejected: " + vendor.getCompanyName());
    }

    public long countPendingApprovals() {
        return vendorRepository.countByStatus(com.vms.entity.Status.PENDING);
    }

    public List<VendorDto> getAllVendors() {
        return vendorRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public com.vms.dto.PageResponse<VendorDto> getAllVendorsPaginated(String companyName, Pageable pageable) {
        Page<Vendor> page = companyName != null && !companyName.isBlank()
                ? vendorRepository.findByCompanyNameContainingIgnoreCase(companyName.trim(), pageable)
                : vendorRepository.findAll(pageable);
        List<VendorDto> content = page.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return com.vms.dto.PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public VendorDto getVendorById(UUID id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + id));
        return convertToDto(vendor);
    }

    public VendorDto createVendor(VendorDto vendorDto) {
        if (vendorRepository.existsByEmail(vendorDto.getEmail())) {
            throw new RuntimeException("Vendor with email " + vendorDto.getEmail() + " already exists");
        }
        com.vms.entity.User vendorUser = null;
        if (vendorDto.getUsername() != null && !vendorDto.getUsername().isBlank()) {
            if (userRepository.existsByUsername(vendorDto.getUsername())) {
                throw new RuntimeException("Username " + vendorDto.getUsername() + " already in use");
            }
            if (vendorDto.getPassword() == null || vendorDto.getPassword().isBlank()) {
                throw new RuntimeException("Password is required when creating vendor login");
            }
            vendorUser = new com.vms.entity.User();
            vendorUser.setUsername(vendorDto.getUsername());
            vendorUser.setEmail(vendorDto.getEmail());
            vendorUser.setPassword(passwordEncoder.encode(vendorDto.getPassword()));
            vendorUser.setStatus(com.vms.entity.Status.ACTIVE);
            vendorUser.setUserRole("VENDOR");
            vendorUser.getRoles().add(
                    roleRepository.findByName(com.vms.entity.RoleName.VENDOR)
                            .orElseThrow(() -> new RuntimeException("Role VENDOR not found")));
            vendorUser = userRepository.save(vendorUser);
        }
        Vendor vendor = new Vendor();
        updateEntity(vendor, vendorDto);
        vendor.setUser(vendorUser);
        vendor.setStatus(com.vms.entity.Status.APPROVED);
        vendor = vendorRepository.save(vendor);
        auditService.log(null, "CREATE_VENDOR", "VENDOR", vendor.getId().toString(), "Vendor created: " + vendor.getCompanyName());
        return convertToDto(vendor);
    }

    public VendorDto updateVendor(UUID id, VendorDto vendorDto) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + id));
        updateEntity(vendor, vendorDto);
        return convertToDto(vendorRepository.save(vendor));
    }

    /** Vendor updates own profile (cannot change status). */
    public VendorDto updateOwnProfile(UUID userId, VendorDto vendorDto) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found for current user"));
        updateEntityForVendorSelf(vendor, vendorDto);
        return convertToDto(vendorRepository.save(vendor));
    }
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductRepository productRepository;
    public void deleteVendor(UUID id) {
        log.info("Deleting vendor with ID: {}", id);

        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + id));

        // 1. Delete purchase orders first (cascades to items, invoices)
        purchaseOrderRepository.deleteAll(
                purchaseOrderRepository.findByVendorId(id)
        );

        // 2. Now safe to delete products
        productRepository.deleteAll(
                productRepository.findByVendorId(id)
        );

        // 3. Delete vendor (cascades category mappings)
        vendorRepository.delete(vendor);

        log.info("Vendor deleted successfully");
    }

    private VendorDto convertToDto(Vendor vendor) {
        VendorDto dto = new VendorDto();
        dto.setId(vendor.getId());
        dto.setCompanyName(vendor.getCompanyName());
        dto.setContactPerson(vendor.getContactPerson());
        dto.setEmail(vendor.getEmail());
        dto.setPhone(vendor.getPhone());
        dto.setAddress(vendor.getAddress());
        dto.setStatus(vendor.getStatus());
        if (vendor.getUser() != null) {
            dto.setUsername(vendor.getUser().getUsername());
        }
        if (vendor.getCategoryMappings() != null && !vendor.getCategoryMappings().isEmpty()) {
            com.vms.entity.VendorCategory cat = vendor.getCategoryMappings().get(0).getCategory();
            dto.setCategory(cat.getName());
            dto.setCategoryId(cat.getId());
        }
        // Include rating information
        dto.setAverageRating(vendor.getAverageRating() != null ? vendor.getAverageRating() : 0.0);
        dto.setTotalRatings(vendor.getTotalRatings() != null ? vendor.getTotalRatings() : 0);
        return dto;
    }

    private void updateEntity(Vendor vendor, VendorDto dto) {
        vendor.setCompanyName(dto.getCompanyName());
        vendor.setContactPerson(dto.getContactPerson());
        vendor.setEmail(dto.getEmail());
        vendor.setPhone(dto.getPhone());
        vendor.setAddress(dto.getAddress());
        if (dto.getStatus() != null) {
            vendor.setStatus(dto.getStatus());
        }
    }

    private void updateEntityForVendorSelf(Vendor vendor, VendorDto dto) {
        if (dto.getCompanyName() != null) vendor.setCompanyName(dto.getCompanyName());
        if (dto.getContactPerson() != null) vendor.setContactPerson(dto.getContactPerson());
        if (dto.getEmail() != null) vendor.setEmail(dto.getEmail());
        if (dto.getPhone() != null) vendor.setPhone(dto.getPhone());
        if (dto.getAddress() != null) vendor.setAddress(dto.getAddress());
        // Status is never updated by vendor
    }
}
