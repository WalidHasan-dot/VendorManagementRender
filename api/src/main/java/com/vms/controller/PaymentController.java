package com.vms.controller;

import com.vms.dto.PaymentDto;
import com.vms.security.UserDetailsImpl;
import com.vms.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public List<PaymentDto> getAllPayments() {
        return paymentService.getAllPayments();
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<?> getPaymentsPage(@PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Payments fetched",
                paymentService.getAllPaymentsPaginated(pageable)));
    }

    @GetMapping("/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> getMyPayments(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Payments fetched",
                paymentService.getPaymentsForVendor(currentUser.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<PaymentDto> getPaymentById(@PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(paymentService.getPaymentByIdForContext(id, currentUser.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<PaymentDto> createPayment(@RequestBody PaymentDto dto) {
        return ResponseEntity.ok(paymentService.createPayment(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE')")
    public ResponseEntity<PaymentDto> updatePayment(@PathVariable UUID id, @RequestBody PaymentDto dto) {
        return ResponseEntity.ok(paymentService.updatePayment(id, dto));
    }

    @PutMapping("/{id}/mark-paid")
    @PreAuthorize("hasRole('FINANCE') or hasRole('VENDOR')")
    public ResponseEntity<PaymentDto> markAsPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.markAsPaid(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePayment(@PathVariable UUID id) {
        paymentService.deletePayment(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/metadata")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<?> getPayslip(@PathVariable UUID id) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse
                .success("Payslip fetched", paymentService.getPayslip(id)));
    }

    @GetMapping("/{id}/payslip")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<?> downloadPayslipPdf(@PathVariable UUID id) {
        // For now, return the metadata - PDF generation can be added later with JasperReports
        return ResponseEntity.ok(com.vms.dto.ApiResponse
                .success("Payslip data fetched", paymentService.getPayslip(id)));
    }
}
