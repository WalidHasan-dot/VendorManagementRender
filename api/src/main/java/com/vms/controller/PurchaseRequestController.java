package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.dto.PurchaseRequestApprovalDto;
import com.vms.dto.PurchaseRequestDto;
import com.vms.security.UserDetailsImpl;
import com.vms.service.PurchaseRequestService;
import com.vms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class PurchaseRequestController {

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private UserService userService;

    // User submits purchase request
    @PostMapping("/purchase-requests")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> submitRequest(@RequestBody PurchaseRequestDto dto, 
                                          @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Purchase request submitted", 
                purchaseRequestService.submitRequest(dto, currentUser.getId())));
    }

    // User sees their requests
    @GetMapping("/purchase-requests/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getMyRequests(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requests fetched", 
                purchaseRequestService.getMyRequests(currentUser.getId())));
    }

    // Admin APIs
    
    @GetMapping("/admin/purchase-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success("Pending requests fetched", 
                purchaseRequestService.getAllPendingRequests()));
    }

    @PutMapping("/admin/purchase-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveRequest(@PathVariable UUID id, 
                                           @RequestBody PurchaseRequestApprovalDto approvalDto,
                                           @AuthenticationPrincipal UserDetailsImpl currentUser) {
        purchaseRequestService.approveRequest(id, approvalDto, userService.getUserById(currentUser.getId()));
        return ResponseEntity.ok(ApiResponse.success("Purchase request approved and PO created", null));
    }

    @PutMapping("/admin/purchase-requests/{id}/deny")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> denyRequest(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserDetailsImpl currentUser) {
        purchaseRequestService.denyRequest(id, userService.getUserById(currentUser.getId()));
        return ResponseEntity.ok(ApiResponse.success("Purchase request denied", null));
    }
}
