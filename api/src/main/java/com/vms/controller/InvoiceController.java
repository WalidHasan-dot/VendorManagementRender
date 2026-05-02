package com.vms.controller;

import com.vms.dto.InvoiceDto;
import com.vms.entity.Status;
import com.vms.entity.User;
import com.vms.repository.UserRepository;
import com.vms.security.UserDetailsImpl;
import com.vms.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public List<InvoiceDto> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }

    @GetMapping("/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> getMyInvoices(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Invoices fetched",
                invoiceService.getInvoicesForVendor(currentUser.getId())));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<?> getInvoicesPage(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Invoices fetched",
                invoiceService.getAllInvoicesPaginated(statusEnum, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<InvoiceDto> getInvoiceById(@PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(invoiceService.getInvoiceByIdForContext(id, currentUser.getId()));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<InvoiceDto> downloadInvoice(@PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        InvoiceDto dto = invoiceService.getInvoiceByIdForContext(id, currentUser.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto);
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> createInvoice(@RequestBody InvoiceDto dto) {
        return ResponseEntity.ok(invoiceService.createInvoice(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> updateInvoice(@PathVariable UUID id, @RequestBody InvoiceDto dto) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, dto));
    }

    @PostMapping("/vendor-submit")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> vendorSubmitInvoice(@RequestBody InvoiceDto dto) {
        return ResponseEntity.ok(invoiceService.vendorSubmitInvoice(dto));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> verifyInvoice(@PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        return ResponseEntity.ok(invoiceService.verifyInvoice(id, user));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDto> approveInvoice(@PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        return ResponseEntity.ok(invoiceService.approveInvoice(id, user));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<InvoiceDto> rejectInvoice(@PathVariable UUID id,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        return ResponseEntity.ok(invoiceService.rejectInvoice(id, user, note));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteInvoice(@PathVariable UUID id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.ok().build();
    }
}
