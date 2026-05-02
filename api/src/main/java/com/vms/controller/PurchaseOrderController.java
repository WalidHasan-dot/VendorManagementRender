package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.dto.PurchaseOrderDto;
import com.vms.dto.PurchaseRequestDto;
import com.vms.dto.StatusTimelineEntryDto;
import com.vms.entity.Status;
import com.vms.security.UserDetailsImpl;
import com.vms.service.PurchaseOrderService;
import com.vms.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private VendorRepository vendorRepository;

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public List<PurchaseOrderDto> getAllPurchaseOrders(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        boolean isAdminOrFinance = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_FINANCE".equals(a.getAuthority()));
        boolean isVendor = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));

        if (isAdminOrFinance) {
            return purchaseOrderService.getAllPurchaseOrders();
        } else if (isVendor) {
            return purchaseOrderService.getPurchaseOrdersForVendor(currentUser.getId());
        } else {
            return purchaseOrderService.getPurchaseOrdersForUser(currentUser.getId());
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderDto>>> getPurchaseOrdersPage(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        UUID userId = null;
        UUID vendorId = null;
        boolean isAdminOrFinance = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_FINANCE".equals(a.getAuthority()));
        boolean isVendor = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));
        if (!isAdminOrFinance) {
            if (isVendor) {
                vendorId = vendorRepository.findByUserId(currentUser.getId())
                        .map(v -> v.getId())
                        .orElse(null);
            } else {
                userId = currentUser.getId();
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Purchase orders fetched",
                purchaseOrderService.getPurchaseOrdersPaginated(userId, vendorId, statusEnum, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        boolean isAdminOrFinance = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_FINANCE".equals(a.getAuthority()));
        boolean isVendor = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));

        return ResponseEntity.ok(
                purchaseOrderService.getPurchaseOrderForUserContext(id, currentUser.getId(), isAdminOrFinance,
                        isVendor));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<StatusTimelineEntryDto>>> getPurchaseOrderTimeline(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        boolean isAdminOrFinance = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_FINANCE".equals(a.getAuthority()));
        boolean isVendor = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));
        return ResponseEntity.ok(
                ApiResponse.success("Timeline fetched",
                        purchaseOrderService.getPurchaseOrderTimeline(id, currentUser.getId(), isAdminOrFinance,
                                isVendor)));
    }
    @GetMapping("/admin/all")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> adminGetAllPurchaseOrders() {
    return ResponseEntity.ok(ApiResponse.success("Purchase orders fetched", purchaseOrderService.getAllPurchaseOrders()));
}

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(@RequestBody PurchaseOrderDto poDto,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.createPurchaseOrder(poDto, currentUser.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable("id") UUID id,
            @RequestBody PurchaseOrderDto poDto) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, poDto));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> submitPurchaseOrder(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(id, currentUser.getId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> approvePurchaseOrder(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        com.vms.entity.User admin = purchaseOrderService.getUserById(currentUser.getId());
        return ResponseEntity.ok(purchaseOrderService.approvePurchaseOrder(id, admin));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> rejectPurchaseOrder(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        com.vms.entity.User admin = purchaseOrderService.getUserById(currentUser.getId());
        return ResponseEntity.ok(purchaseOrderService.rejectPurchaseOrder(id, admin));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PurchaseOrderDto> createPurchaseRequest(@RequestBody PurchaseRequestDto request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.createPurchaseRequest(request, currentUser.getId()));
    }

    @PostMapping("/{id}/mark-delivered")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<PurchaseOrderDto> markDelivered(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.markDeliveredByVendor(id, currentUser.getId()));
    }

    @PostMapping("/{id}/vendor-accept")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<PurchaseOrderDto> vendorAccept(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.acceptOrderByVendor(id, currentUser.getId()));
    }

    @PostMapping("/{id}/vendor-reject")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<PurchaseOrderDto> vendorReject(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.rejectOrderByVendor(id, currentUser.getId()));
    }

    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PurchaseOrderDto> confirmDelivery(@PathVariable("id") UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(purchaseOrderService.confirmDelivery(id, currentUser.getId()));
    }

    @PostMapping("/{id}/assign-vendor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseOrderDto> assignVendor(@PathVariable("id") UUID id,
            @RequestParam UUID vendorId,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        com.vms.entity.User admin = purchaseOrderService.getUserById(currentUser.getId());
        return ResponseEntity.ok(purchaseOrderService.assignVendor(id, vendorId, admin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePurchaseOrder(@PathVariable("id") UUID id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.ok().build();
    }
}
