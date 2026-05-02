package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.dto.VendorRatingDto;
import com.vms.security.UserDetailsImpl;
import com.vms.service.PurchaseRequestService;
import com.vms.service.VendorRatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class UserDashboardController {

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private VendorRatingService vendorRatingService;

    @GetMapping("/user/dashboard-summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getDashboardSummary(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary fetched", 
                purchaseRequestService.getDashboardSummary(currentUser.getId())));
    }

    @GetMapping("/user/completed-orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getCompletedOrders(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Completed orders fetched", 
                vendorRatingService.getCompletedOrdersForRating(currentUser.getId())));
    }

    @PostMapping("/vendor-ratings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> rateVendor(@RequestBody VendorRatingDto dto, 
                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Vendor rated successfully", 
                vendorRatingService.rateVendor(dto.getVendorId(), dto, currentUser.getId())));
    }
}
