package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.entity.Status;
import com.vms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vms.security.UserDetailsImpl;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping("/dashboard/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdminDashboard(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("pendingUserApprovals", userRepository.countByStatus(Status.PENDING));
        stats.put("totalVendors", vendorRepository.count());
        stats.put("pendingVendorApprovals", vendorRepository.countByStatus(Status.PENDING));
        stats.put("totalPurchaseOrders", purchaseOrderRepository.count());
        stats.put("activePurchaseOrders", purchaseOrderRepository.count() - purchaseOrderRepository.countByStatus(Status.COMPLETED)
                - purchaseOrderRepository.countByStatus(Status.CANCELLED) - purchaseOrderRepository.countByStatus(Status.REJECTED));
        stats.put("pendingInvoices", invoiceRepository.countByStatus(Status.PENDING));
        stats.put("completedPayments", paymentRepository.count());
        stats.put("totalInvoices", invoiceRepository.count());
        Double totalRevenue = paymentRepository.sumTotalRevenue();
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        stats.put("recentActivities", auditLogRepository.findTop10ByOrderByTimestampDesc().stream()
                .map(com.vms.dto.EntityMapper::mapToAuditLogDto).toList());
        return ResponseEntity.ok(ApiResponse.success("Admin dashboard data fetched", stats));
    }

    @GetMapping("/dashboard/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> getVendorDashboard(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        com.vms.entity.User user = new com.vms.entity.User();
        user.setId(userDetails.getId());
        com.vms.entity.Vendor vendor = vendorRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        var allOrders = purchaseOrderRepository.findByVendorId(vendor.getId());

        long pending   = allOrders.stream().filter(po -> po.getStatus() == Status.PENDING).count();
        long accepted  = allOrders.stream().filter(po -> po.getStatus() == Status.APPROVED).count();
        long delivered = allOrders.stream().filter(po -> po.getStatus() == Status.DELIVERED).count();

        var payments = paymentRepository.findByInvoice_PurchaseOrder_VendorId(vendor.getId());
        double totalPaid = payments.stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                .sum();

        // Get 5 most recent orders for the table
        var recentOrders = allOrders.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(po -> {
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", po.getId());
                    order.put("totalAmount", po.getTotalAmount());
                    order.put("status", po.getStatus());
                    order.put("createdAt", po.getCreatedAt());
                    return order;
                })
                .toList();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAssignedOrders", allOrders.size());  // ← renamed
        stats.put("pendingOrders", pending);                  // ← added
        stats.put("acceptedOrders", accepted);                // ← added
        stats.put("deliveredOrders", delivered);              // ← added
        stats.put("paymentsReceived", totalPaid);             // ← renamed + summed
        stats.put("recentOrders", recentOrders);              // ← added
        stats.put("status", vendor.getStatus());

        return ResponseEntity.ok(ApiResponse.success("Vendor dashboard data fetched", stats));
    }

    @GetMapping("/dashboard/user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserDashboard(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        var myOrders = purchaseOrderRepository.findByCreatedById(userDetails.getId());

        long pending = myOrders.stream().filter(po -> po.getStatus() == Status.PENDING).count();
        long approved = myOrders.stream().filter(po -> po.getStatus() == Status.APPROVED).count();
        long delivered = myOrders.stream().filter(po -> po.getStatus() == Status.DELIVERED).count();
        long completed = myOrders.stream().filter(po -> po.getStatus() == Status.COMPLETED).count();

        var mappedOrders = myOrders.stream().map(po -> {
            Map<String, Object> order = new HashMap<>();
            order.put("id", po.getId());
            order.put("totalAmount", po.getTotalAmount());
            order.put("status", po.getStatus());
            order.put("createdAt", po.getCreatedAt());
            return order;
        }).toList();

        Map<String, Object> stats = new HashMap<>();
        stats.put("myPurchaseOrders", mappedOrders);
        stats.put("pendingRequests", pending);
        stats.put("approvedRequests", approved);
        stats.put("deliveredOrders", delivered);
        stats.put("completedOrders", completed);
        stats.put("myNotifications", notificationRepository.countByUserId(userDetails.getId()));
        return ResponseEntity.ok(ApiResponse.success("User dashboard data fetched", stats));
    }

    @GetMapping("/dashboard/finance")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<?> getFinanceDashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalInvoices", invoiceRepository.count());
        stats.put("paidInvoices", invoiceRepository.countByStatus(Status.PAID));
        stats.put("pendingInvoices", invoiceRepository.countByStatus(Status.PENDING));
        stats.put("verifiedInvoices", invoiceRepository.countByStatus(Status.APPROVED));
        var allPayments = paymentRepository.findAll();
        double totalPaid = allPayments.stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                .sum();
        stats.put("completedPayments", paymentRepository.count());  // count for the stat card
        stats.put("totalPayments", totalPaid);                       // dollar sum for display
        stats.put("paymentHistory", allPayments.stream()
                .map(com.vms.dto.EntityMapper::mapToPaymentDto).toList()); // Angular uses this for the table
        stats.put("monthlyPaymentStats", invoiceRepository.getMonthlyRevenue()); // for the bar chart
        Double totalRevenue = paymentRepository.sumTotalRevenue();
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        stats.put("monthlyRevenue", invoiceRepository.getMonthlyRevenue());
        return ResponseEntity.ok(ApiResponse.success("Finance dashboard data fetched", stats));
    }

    @GetMapping("/dashboard/finance/revenue-chart")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<?> getRevenueChart() {
        return ResponseEntity
                .ok(ApiResponse.success("Monthly revenue data fetched", invoiceRepository.getMonthlyRevenue()));
    }
}
