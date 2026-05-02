package com.vms.service;

import com.vms.dto.PurchaseRequestApprovalDto;
import com.vms.dto.PurchaseRequestDto;
import com.vms.entity.*;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseRequestService {

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotificationService notificationService;

    public PurchaseRequestDto submitRequest(PurchaseRequestDto dto, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PurchaseRequest request = new PurchaseRequest();
        request.setUser(user);
        request.setProductName(dto.getProductName());
        request.setQuantity(dto.getQuantity());
        request.setRequiredDate(dto.getRequiredDate());
        request.setPurpose(dto.getPurpose());
        request.setStatus(Status.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        request = purchaseRequestRepository.save(request);

        auditService.log(user, "SUBMIT_PURCHASE_REQUEST", "PURCHASE_REQUEST", request.getId().toString(), 
                "User submitted purchase request for " + dto.getProductName());
        
        notificationService.notifyAdmins("New purchase request from " + user.getUsername() + " for " + dto.getProductName());

        return convertToDto(request);
    }

    public List<PurchaseRequestDto> getMyRequests(UUID userId) {
        return purchaseRequestRepository.findByUserId(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<PurchaseRequestDto> getAllPendingRequests() {
        return purchaseRequestRepository.findByStatus(Status.PENDING).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public void approveRequest(UUID requestId, PurchaseRequestApprovalDto approvalDto, User admin) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Request not found"));

        if (request.getStatus() != Status.PENDING) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only pending requests can be approved"
            );
        }

        Vendor vendor = vendorRepository.findById(approvalDto.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        // 1. Update purchase_requests status
        request.setStatus(Status.APPROVED);
        purchaseRequestRepository.save(request);

        // 2. Create record in purchase_orders
        PurchaseOrder po = new PurchaseOrder();
        po.setVendor(vendor);
        po.setCreatedBy(request.getUser()); // Original requester or Admin? Let's say Original Requester
        po.setTotalAmount(approvalDto.getQuantity() * approvalDto.getPrice());
        po.setStatus(Status.APPROVED); // Ready for vendor to accept
        po.setDeliveryType(approvalDto.getDeliveryType());
        po.setDeliveryUserId(approvalDto.getDeliveryUserId());
        po.setCreatedAt(LocalDateTime.now());
        po.setItems(new ArrayList<>());

        // 3. Insert items in purchase_order_items
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        
        // Find or create a temporary product if price/name changed? 
        // The modal allows editing product name and price. 
        // Let's look for existing product by name, if not exists, create a dummy or just use ID if provided.
        // For simplicity, let's try to find the product by name.
        Product product = productRepository.findByNameIgnoreCase(approvalDto.getProductName())
                .orElse(null);
        
        if (product == null) {
            product = new Product();
            product.setName(approvalDto.getProductName());
            product.setPrice(approvalDto.getPrice());
            product.setVendor(vendor);
            product = productRepository.save(product);
        }

        item.setProduct(product);
        item.setQuantity(approvalDto.getQuantity());
        item.setPrice(approvalDto.getPrice());
        po.getItems().add(item);

        purchaseOrderRepository.save(po);

        // 4. Send notification to assigned vendor
        if (vendor.getUser() != null) {
            notificationService.createNotification(vendor.getUser(), 
                    "New purchase order created from request: #" + po.getId().toString().substring(0, 8));
        }

        auditService.log(admin, "APPROVE_PURCHASE_REQUEST", "PURCHASE_REQUEST", request.getId().toString(), 
                "Admin approved purchase request and created PO #" + po.getId());
        
        notificationService.createNotification(request.getUser(), 
                "Your purchase request for " + request.getProductName() + " has been approved.");
    }

    public void denyRequest(UUID requestId, User admin) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Request not found"));

        if (request.getStatus() != Status.PENDING) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only pending requests can be denied"
            );
        }

        request.setStatus(Status.DENIED);
        purchaseRequestRepository.save(request);

        auditService.log(admin, "DENY_PURCHASE_REQUEST", "PURCHASE_REQUEST", request.getId().toString(), 
                "Admin denied purchase request");
        
        notificationService.createNotification(request.getUser(), 
                "Your purchase request for " + request.getProductName() + " has been denied.");
    }

    public java.util.Map<String, Long> getDashboardSummary(UUID userId) {
        java.util.Map<String, Long> summary = new java.util.HashMap<>();
        summary.put("total_requests", purchaseRequestRepository.countByUserId(userId));
        summary.put("pending_requests", purchaseRequestRepository.countByUserIdAndStatus(userId, Status.PENDING));
        summary.put("approved_requests", purchaseRequestRepository.countByUserIdAndStatus(userId, Status.APPROVED));
        summary.put("delivered_orders", purchaseOrderRepository.countByCreatedByIdAndStatus(userId, Status.DELIVERED));
        return summary;
    }

    private PurchaseRequestDto convertToDto(PurchaseRequest request) {
        PurchaseRequestDto dto = new PurchaseRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUser().getId());
        dto.setUserName(request.getUser().getUsername());
        dto.setProductName(request.getProductName());
        dto.setQuantity(request.getQuantity());
        dto.setRequiredDate(request.getRequiredDate());
        dto.setPurpose(request.getPurpose());
        dto.setStatus(request.getStatus().name());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }
}
