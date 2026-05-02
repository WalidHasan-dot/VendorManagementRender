package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.dto.PurchaseOrderDto;
import com.vms.security.UserDetailsImpl;
import com.vms.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
public class AdminPurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> getAllPurchaseOrders() {
        return ResponseEntity.ok(ApiResponse.success("Purchase orders fetched", 
                purchaseOrderService.getAllPurchaseOrders()));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> createPurchaseOrder(@RequestBody PurchaseOrderDto poDto,
                                                @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order created", 
                purchaseOrderService.createPurchaseOrder(poDto, currentUser.getId())));
    }
}
