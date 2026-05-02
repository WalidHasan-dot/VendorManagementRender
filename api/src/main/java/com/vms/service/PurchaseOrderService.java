package com.vms.service;

import com.vms.dto.PurchaseOrderDto;
import com.vms.dto.PurchaseOrderItemDto;
import com.vms.entity.*;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.ProductRepository;
import com.vms.repository.PurchaseOrderRepository;
import com.vms.repository.UserRepository;
import com.vms.repository.VendorRepository;
import com.vms.repository.AuditLogRepository;

import com.vms.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vms.dto.PurchaseRequestDto;
import com.vms.dto.StatusTimelineEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseOrderService {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    public List<PurchaseOrderDto> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Purchase orders created by the given user (for USER dashboard/views).
     */
    public List<PurchaseOrderDto> getPurchaseOrdersForUser(UUID userId) {
        return purchaseOrderRepository.findByCreatedById(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Purchase orders assigned to the vendor linked to the given user (for VENDOR dashboard/views).
     */
    public List<PurchaseOrderDto> getPurchaseOrdersForVendor(UUID userId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));

        return purchaseOrderRepository.findByVendorId(vendor.getId()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Page<PurchaseOrderDto> getPurchaseOrdersPaginated(UUID userId, UUID vendorId, Status status, Pageable pageable) {
        Page<PurchaseOrder> page;
        if (vendorId != null) {
            page = purchaseOrderRepository.findByVendorId(vendorId, pageable);
        } else if (userId != null) {
            page = purchaseOrderRepository.findByCreatedById(userId, pageable);
        } else if (status != null) {
            page = purchaseOrderRepository.findByStatus(status, pageable);
        } else {
            page = purchaseOrderRepository.findAll(pageable);
        }
        return page.map(this::convertToDto);
    }

    public PurchaseOrderDto getPurchaseOrderById(UUID id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        return convertToDto(po);
    }

    /**
     * Timeline view derived from audit logs for a given purchase order, respecting
     * visibility constraints.
     */
    public List<StatusTimelineEntryDto> getPurchaseOrderTimeline(UUID id, UUID userId, boolean isAdminOrFinance,
            boolean isVendor) {
        // Will throw if user cannot see this PO
        getPurchaseOrderForUserContext(id, userId, isAdminOrFinance, isVendor);

        List<AuditLog> logs = auditLogRepository.findByEntityAndEntityIdOrderByTimestampDesc("PURCHASE_ORDER",
                id.toString());

        return logs.stream().map(log -> {
            StatusTimelineEntryDto dto = new StatusTimelineEntryDto();
            dto.setAction(log.getAction());
            dto.setDetails(log.getDetails());
            dto.setTimestamp(log.getTimestamp());
            dto.setPerformedBy(log.getUser() != null ? log.getUser().getUsername() : "SYSTEM");
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Simple purchase request flow for standard users. Creates a purchase order
     * with a single line item derived from product name, and links it to the
     * authenticated user.
     */
    public PurchaseOrderDto createPurchaseRequest(PurchaseRequestDto request, UUID userId) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Product product = productRepository.findByNameIgnoreCase(request.getProductName())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with name: " + request.getProductName()));

        Vendor vendor = product.getVendor();
        if (vendor == null) {
            throw new IllegalStateException("Product is not associated with any vendor");
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setCreatedBy(user);
        po.setVendor(vendor);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(product);
        item.setQuantity(request.getQuantity());
        item.setPrice(product.getPrice());
        item.setPurchaseOrder(po);

        po.setItems(new ArrayList<>());
        po.getItems().add(item);

        double total = request.getQuantity() * product.getPrice();
        po.setTotalAmount(total);
        po.setStatus(Status.PENDING); // user-request initial state

        po = purchaseOrderRepository.save(po);

        String details = "Purchase request for " + request.getQuantity() + "x " + product.getName();
        if (request.getPurpose() != null && !request.getPurpose().isBlank()) {
            details += " | Purpose: " + request.getPurpose();
        }
        if (request.getRequiredDate() != null) {
            details += " | Required by: " + request.getRequiredDate();
        }

        auditService.log(user, "CREATE_PURCHASE_REQUEST", "PURCHASE_ORDER", po.getId().toString(), details);
        notificationService.notifyAdmins("New purchase request from " + user.getUsername()
                + " for " + request.getQuantity() + "x " + product.getName());

        return convertToDto(po);
    }

    /**
     * Create a purchase order on behalf of the authenticated user. The creator is
     * taken from
     * the security context (via controller) and not from client-supplied data.
     */
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto poDto, UUID creatorId) {
        PurchaseOrder po = new PurchaseOrder();
        updateEntity(po, poDto);
        if (po.getVendor() != null) {
            po.setStatus(Status.APPROVED);
        } else {
            po.setStatus(Status.DRAFT);
        }
        User creator = null;
        if (creatorId != null) {
            creator = userRepository.findById(creatorId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + creatorId));
            po.setCreatedBy(creator);
        }
        po = purchaseOrderRepository.save(po);
        auditService.log(creator, "CREATE_PO", "PURCHASE_ORDER", po.getId().toString(), "Purchase order created");
        if (po.getVendor() != null && po.getVendor().getUser() != null) {
            notificationService.createNotification(po.getVendor().getUser(),
                    "New purchase order assigned to you: #" + po.getId().toString().substring(0, 8));
        }
        notificationService.notifyAdmins("New purchase order created: #" + po.getId().toString().substring(0, 8));
        return convertToDto(po);
    }

    public PurchaseOrderDto updatePurchaseOrder(UUID id, PurchaseOrderDto poDto) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        updateEntity(po, poDto);
        return convertToDto(purchaseOrderRepository.save(po));
    }

    public void deletePurchaseOrder(UUID id) {
        purchaseOrderRepository.deleteById(id);
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    /** User submits PO for admin approval. DRAFT -> SUBMITTED */
    public PurchaseOrderDto submitPurchaseOrder(UUID id, UUID userId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        if (po.getCreatedBy() == null || !po.getCreatedBy().getId().equals(userId)) {
            throw new ResourceNotFoundException("Only the creator can submit this purchase order");
        }
        if (po.getStatus() != Status.DRAFT) {
            throw new IllegalStateException("Only DRAFT purchase orders can be submitted");
        }
        po.setStatus(Status.SUBMITTED);
        po = purchaseOrderRepository.save(po);
        User user = userRepository.findById(userId).orElse(null);
        auditService.log(user, "SUBMIT_PO", "PURCHASE_ORDER", po.getId().toString(), "Purchase order submitted for approval");
        return convertToDto(po);
    }

    /** Admin approves PO. SUBMITTED or VENDOR_ASSIGNED -> APPROVED. Notifies vendor. */
    public PurchaseOrderDto approvePurchaseOrder(UUID id, User admin) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        if (po.getStatus() != Status.SUBMITTED && po.getStatus() != Status.VENDOR_ASSIGNED) {
            throw new IllegalStateException("Only SUBMITTED or VENDOR_ASSIGNED purchase orders can be approved");
        }
        po.setStatus(Status.APPROVED);
        po = purchaseOrderRepository.save(po);
        auditService.log(admin, "APPROVE_PO", "PURCHASE_ORDER", po.getId().toString(), "Purchase order approved");
        if (po.getVendor() != null && po.getVendor().getUser() != null) {
            notificationService.createNotification(po.getVendor().getUser(),
                    "Purchase order #" + po.getId().toString().substring(0, 8) + " has been approved");
        }
        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Your purchase request #" + po.getId().toString().substring(0, 8) + " has been APPROVED");
        }
        return convertToDto(po);
    }

    /** Admin rejects PO. SUBMITTED or VENDOR_ASSIGNED -> REJECTED */
    public PurchaseOrderDto rejectPurchaseOrder(UUID id, User admin) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        if (po.getStatus() != Status.SUBMITTED && po.getStatus() != Status.VENDOR_ASSIGNED) {
            throw new IllegalStateException("Only SUBMITTED or VENDOR_ASSIGNED purchase orders can be rejected");
        }
        po.setStatus(Status.REJECTED);
        po = purchaseOrderRepository.save(po);
        auditService.log(admin, "REJECT_PO", "PURCHASE_ORDER", po.getId().toString(), "Purchase order rejected");
        if (po.getVendor() != null && po.getVendor().getUser() != null) {
            notificationService.createNotification(po.getVendor().getUser(),
                    "Purchase order #" + po.getId().toString().substring(0, 8) + " has been REJECTED by admin");
        }
        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Your purchase request #" + po.getId().toString().substring(0, 8) + " has been REJECTED");
        }
        return convertToDto(po);
    }

    /** Vendor accepts an order. APPROVED or VENDOR_ASSIGNED -> VENDOR_ACCEPTED */
    public PurchaseOrderDto acceptOrderByVendor(UUID id, UUID vendorUserId) {
        Vendor vendor = vendorRepository.findByUserId(vendorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));

        if (po.getVendor() == null || !vendor.getId().equals(po.getVendor().getId())) {
            throw new ResourceNotFoundException("Purchase Order not assigned to current vendor");
        }
        if (po.getStatus() == Status.VENDOR_ACCEPTED) {
            return convertToDto(po);
        }
        if (po.getStatus() != Status.APPROVED && po.getStatus() != Status.VENDOR_ASSIGNED) {
            throw new IllegalStateException("Only APPROVED or VENDOR_ASSIGNED orders can be accepted by vendor. Current status: " + po.getStatus());
        }

        po.setStatus(Status.VENDOR_ACCEPTED);
        po = purchaseOrderRepository.save(po);

        auditService.log(vendor.getUser(), "VENDOR_ACCEPT_ORDER", "PURCHASE_ORDER", po.getId().toString(),
                "Vendor accepted the order");

        notificationService.notifyAdmins("Vendor " + vendor.getCompanyName() + " accepted order #"
                + po.getId().toString().substring(0, 8));
        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Vendor " + vendor.getCompanyName() + " accepted your order #"
                            + po.getId().toString().substring(0, 8));
        }

        return convertToDto(po);
    }

    /** Vendor rejects an order. APPROVED or VENDOR_ASSIGNED -> REJECTED */
    public PurchaseOrderDto rejectOrderByVendor(UUID id, UUID vendorUserId) {
        Vendor vendor = vendorRepository.findByUserId(vendorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));

        if (po.getVendor() == null || !vendor.getId().equals(po.getVendor().getId())) {
            throw new ResourceNotFoundException("Purchase Order not assigned to current vendor");
        }
        if (po.getStatus() == Status.REJECTED) {
            return convertToDto(po);
        }
        if (po.getStatus() != Status.APPROVED && po.getStatus() != Status.VENDOR_ASSIGNED) {
            throw new IllegalStateException("Only APPROVED or VENDOR_ASSIGNED orders can be rejected by vendor. Current status: " + po.getStatus());
        }

        po.setStatus(Status.REJECTED);
        po = purchaseOrderRepository.save(po);

        auditService.log(vendor.getUser(), "VENDOR_REJECT_ORDER", "PURCHASE_ORDER", po.getId().toString(),
                "Vendor rejected the order");

        notificationService.notifyAdmins("Vendor " + vendor.getCompanyName() + " rejected order #"
                + po.getId().toString().substring(0, 8));
        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Vendor " + vendor.getCompanyName() + " rejected your order #"
                            + po.getId().toString().substring(0, 8));
        }

        return convertToDto(po);
    }

    /** Vendor marks order as delivered. APPROVED or VENDOR_ASSIGNED -> DELIVERED */
    public PurchaseOrderDto markDeliveredByVendor(UUID id, UUID vendorUserId) {
        Vendor vendor = vendorRepository.findByUserId(vendorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));

        if (po.getVendor() == null || !vendor.getId().equals(po.getVendor().getId())) {
            throw new ResourceNotFoundException("Purchase Order not assigned to current vendor");
        }
        if (po.getStatus() == Status.DELIVERED) {
            return convertToDto(po);
        }
        if (po.getStatus() != Status.VENDOR_ACCEPTED) {
            throw new IllegalStateException("Only VENDOR_ACCEPTED orders can be marked as delivered. Current status: " + po.getStatus());
        }

        po.setStatus(Status.DELIVERED);
        po = purchaseOrderRepository.save(po);

        auditService.log(vendor.getUser(), "MARK_DELIVERED", "PURCHASE_ORDER", po.getId().toString(),
                "Vendor marked order as DELIVERED");

        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Order #" + po.getId().toString().substring(0, 8) + " has been marked as DELIVERED by vendor");
        }
        notificationService.notifyAdmins("Order #" + po.getId().toString().substring(0, 8)
                + " has been marked as DELIVERED by vendor");

        return convertToDto(po);
    }

    /** User confirms delivery. DELIVERED -> COMPLETED */
    public PurchaseOrderDto confirmDelivery(UUID id, UUID userId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));

        if (po.getCreatedBy() == null || !po.getCreatedBy().getId().equals(userId)) {
            throw new ResourceNotFoundException("Purchase Order not accessible for current user");
        }
        if (po.getStatus() != Status.DELIVERED) {
            throw new IllegalStateException("Only DELIVERED orders can be confirmed");
        }

        po.setStatus(Status.COMPLETED);
        po = purchaseOrderRepository.save(po);

        User user = userRepository.findById(userId).orElse(null);
        auditService.log(user, "CONFIRM_DELIVERY", "PURCHASE_ORDER", po.getId().toString(),
                "User confirmed delivery");

        return convertToDto(po);
    }

    /** Admin assigns vendor to PO. Notifies vendor. SUBMITTED -> VENDOR_ASSIGNED or APPROVED. */
    public PurchaseOrderDto assignVendor(UUID id, UUID vendorId, User admin) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));
        if (po.getStatus() != Status.SUBMITTED && po.getStatus() != Status.DRAFT) {
            throw new IllegalStateException("Only SUBMITTED or DRAFT purchase orders can have vendor assigned");
        }
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + vendorId));
        po.setVendor(vendor);
        po.setStatus(Status.VENDOR_ASSIGNED);
        po = purchaseOrderRepository.save(po);
        auditService.log(admin, "ASSIGN_VENDOR_PO", "PURCHASE_ORDER", po.getId().toString(),
                "Vendor " + vendor.getCompanyName() + " assigned to purchase order");
        if (vendor.getUser() != null) {
            notificationService.createNotification(vendor.getUser(),
                    "New purchase order assigned to you: #" + po.getId().toString().substring(0, 8));
        }
        notificationService.notifyAdmins("Vendor " + vendor.getCompanyName() + " assigned to PO #" + po.getId().toString().substring(0, 8));
        if (po.getCreatedBy() != null) {
            notificationService.createNotification(po.getCreatedBy(),
                    "Vendor " + vendor.getCompanyName() + " has been assigned to your request #"
                            + po.getId().toString().substring(0, 8));
        }
        return convertToDto(po);
    }

    /**
     * Fetch a single purchase order enforcing visibility rules based on the caller
     * context.
     */
    public PurchaseOrderDto getPurchaseOrderForUserContext(UUID id, UUID userId, boolean isAdminOrFinance,
            boolean isVendor) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with id: " + id));

        if (isAdminOrFinance) {
            return convertToDto(po);
        }

        if (isVendor) {
            Vendor vendor = vendorRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));
            if (po.getVendor() == null || !vendor.getId().equals(po.getVendor().getId())) {
                throw new ResourceNotFoundException("Purchase Order not accessible for current vendor");
            }
            return convertToDto(po);
        }

        // Default: treat as standard USER – only own purchase orders are visible
        if (po.getCreatedBy() == null || !userId.equals(po.getCreatedBy().getId())) {
            throw new ResourceNotFoundException("Purchase Order not accessible for current user");
        }

        return convertToDto(po);
    }

    private PurchaseOrderDto convertToDto(PurchaseOrder po) {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setId(po.getId());
        dto.setTotalAmount(po.getTotalAmount());
        dto.setCreatedAt(po.getCreatedAt());
        dto.setDeliveryType(po.getDeliveryType());
        dto.setDeliveryUserId(po.getDeliveryUserId());
        if (po.getStatus() != null) {
            dto.setStatus(po.getStatus().name());
        }
        dto.setRequiredDate(po.getRequiredDate());
        if (po.getVendor() != null) {
            dto.setVendorId(po.getVendor().getId());
            dto.setVendorName(po.getVendor().getCompanyName());
        }
        if (po.getCreatedBy() != null) {
            dto.setCreatedById(po.getCreatedBy().getId());
            dto.setCreatedByUsername(po.getCreatedBy().getUsername());
        }
        if (po.getItems() != null) {
            int totalQty = 0;
            dto.setItems(po.getItems().stream().map(item -> {
                PurchaseOrderItemDto itemDto = new PurchaseOrderItemDto();
                itemDto.setId(item.getId());
                itemDto.setProductId(item.getProduct().getId());
                itemDto.setProductName(item.getProduct().getName());
                itemDto.setProductDescription(item.getProduct().getDescription());
                itemDto.setQuantity(item.getQuantity());
                itemDto.setPrice(item.getPrice());
                return itemDto;
            }).collect(Collectors.toList()));
            
            for (PurchaseOrderItem item : po.getItems()) {
                if (item.getQuantity() != null) totalQty += item.getQuantity();
            }
            dto.setTotalQuantity(totalQty);
        }
        return dto;
    }

    private void updateEntity(PurchaseOrder po, PurchaseOrderDto dto) {
       double total = 0;

        if (dto.getItems() != null) {
             for (PurchaseOrderItemDto itemDto : dto.getItems()) {
        total += itemDto.getQuantity() * itemDto.getPrice();
            }
        }

        po.setTotalAmount(total);
        po.setDeliveryType(dto.getDeliveryType());
        po.setDeliveryUserId(dto.getDeliveryUserId());
        if (dto.getStatus() != null) {
            po.setStatus(Status.valueOf(dto.getStatus()));
        }
        if (dto.getVendorId() != null) {
            Vendor vendor = vendorRepository.findById(dto.getVendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + dto.getVendorId()));
            po.setVendor(vendor);
        }

        if (dto.getItems() != null) {
            if (po.getItems() == null) {
                po.setItems(new ArrayList<>());
            } else {
                po.getItems().removeIf(i -> true);
            }
            for (PurchaseOrderItemDto itemDto : dto.getItems()) {
                PurchaseOrderItem item = new PurchaseOrderItem();
                Product product = productRepository.findById(itemDto.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product not found with id: " + itemDto.getProductId()));
                item.setProduct(product);
                item.setQuantity(itemDto.getQuantity());
                item.setPrice(itemDto.getPrice());
                item.setPurchaseOrder(po);
                po.getItems().add(item);
            }
        }
    }
}
