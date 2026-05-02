package com.vms.service;

import com.vms.dto.InvoiceDto;
import com.vms.entity.Invoice;
import com.vms.entity.PurchaseOrder;
import com.vms.entity.User;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.InvoiceRepository;
import com.vms.repository.PurchaseOrderRepository;
import com.vms.repository.UserRepository;
import com.vms.repository.VendorRepository;
import com.vms.security.UserDetailsImpl;
import com.vms.entity.PurchaseOrderItem;
import com.vms.entity.Status;
import com.vms.dto.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private NotificationService notificationService;

    public List<InvoiceDto> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public PageResponse<InvoiceDto> getAllInvoicesPaginated(Status status, Pageable pageable) {
        Page<Invoice> page = status != null
                ? invoiceRepository.findByStatus(status, pageable)
                : invoiceRepository.findAll(pageable);
        List<InvoiceDto> content = page.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public InvoiceDto getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        return convertToDto(invoice);
    }

    public List<InvoiceDto> getInvoicesForVendor(UUID userId) {
        var vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));
        return invoiceRepository.findByPurchaseOrder_VendorId(vendor.getId()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public InvoiceDto getInvoiceByIdForContext(UUID id, UUID userId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        var vendorOpt = vendorRepository.findByUserId(userId);
        if (vendorOpt.isPresent()) {
            if (invoice.getPurchaseOrder() == null || invoice.getPurchaseOrder().getVendor() == null
                    || !invoice.getPurchaseOrder().getVendor().getId().equals(vendorOpt.get().getId())) {
                throw new ResourceNotFoundException("Invoice not accessible for current vendor");
            }
        }
        return convertToDto(invoice);
    }

    public InvoiceDto createInvoice(InvoiceDto dto) {
        Invoice invoice = new Invoice();
        updateEntity(invoice, dto);
        invoice.setStatus(Status.PENDING); // new invoices start as PENDING; finance approves
        invoice = invoiceRepository.save(invoice);
        User currentUser = getCurrentUser();
        auditService.log(currentUser, "UPLOAD_INVOICE", "INVOICE", invoice.getId().toString(), "Invoice created");
        notificationService.notifyFinance("New invoice uploaded for PO #" + (invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getId().toString().substring(0, 8) : ""));
        notificationService.notifyAdmins("Vendor invoice submission: New invoice for PO #" + (invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getId().toString().substring(0, 8) : ""));
        return convertToDto(invoice);
    }

    public InvoiceDto vendorSubmitInvoice(InvoiceDto dto) {
        Invoice invoice = new Invoice();
        updateEntity(invoice, dto);
        invoice.setStatus(Status.INVOICE_SUBMITTED);
        invoice = invoiceRepository.save(invoice);
        User currentUser = getCurrentUser();
        auditService.log(currentUser, "VENDOR_SUBMIT_INVOICE", "INVOICE", invoice.getId().toString(), "Invoice submitted by vendor");
        notificationService.notifyFinance("New invoice submitted for PO #" + (invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getId().toString().substring(0, 8) : ""));
        notificationService.notifyAdmins("Vendor invoice submission: New invoice for PO #" + (invoice.getPurchaseOrder() != null ? invoice.getPurchaseOrder().getId().toString().substring(0, 8) : ""));
        return convertToDto(invoice);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            return userRepository.findById(((UserDetailsImpl) auth.getPrincipal()).getId()).orElse(null);
        }
        return null;
    }

    public InvoiceDto updateInvoice(UUID id, InvoiceDto dto) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        updateEntity(invoice, dto);
        return convertToDto(invoiceRepository.save(invoice));
    }

    public InvoiceDto verifyInvoice(UUID id, User financeUser) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        if (invoice.getStatus() != Status.SUBMITTED && invoice.getStatus() != Status.PENDING && invoice.getStatus() != Status.INVOICE_SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED, INVOICE_SUBMITTED, or PENDING invoices can be verified");
        }
        invoice.setStatus(Status.INVOICED);
        invoice = invoiceRepository.save(invoice);
        auditService.log(financeUser, "VERIFY_INVOICE", "INVOICE", invoice.getId().toString(), "Invoice verified");
        return convertToDto(invoice);
    }

    /** Finance approves invoice. PENDING/VERIFIED -> APPROVED */
    public InvoiceDto approveInvoice(UUID id, User financeUser) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        // allow approving from submitted or verified or pending
        invoice.setStatus(Status.APPROVED);
        invoice = invoiceRepository.save(invoice);
        auditService.log(financeUser, "APPROVE_INVOICE", "INVOICE", invoice.getId().toString(), "Invoice approved");
        return convertToDto(invoice);
    }

    /** Finance or Vendor rejects invoice. PENDING -> REJECTED */
    public InvoiceDto rejectInvoice(UUID id, User user, String note) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        
        // If user is VENDOR, check if it's their invoice
        var vendorOpt = vendorRepository.findByUserId(user.getId());
        if (vendorOpt.isPresent()) {
            if (invoice.getPurchaseOrder() == null || invoice.getPurchaseOrder().getVendor() == null
                    || !invoice.getPurchaseOrder().getVendor().getId().equals(vendorOpt.get().getId())) {
                throw new IllegalStateException("You can only reject your own invoices");
            }
        }

        if (invoice.getStatus() != Status.PENDING && invoice.getStatus() != Status.SUBMITTED && invoice.getStatus() != Status.INVOICE_SUBMITTED) {
            throw new IllegalStateException("Only PENDING or SUBMITTED invoices can be rejected");
        }
        invoice.setStatus(Status.REJECTED);
        invoice.setRejectionNote(note);
        invoice = invoiceRepository.save(invoice);
        auditService.log(user, "REJECT_INVOICE", "INVOICE", invoice.getId().toString(), "Invoice rejected. Note: " + note);
        return convertToDto(invoice);
    }

    public void deleteInvoice(UUID id) {
        invoiceRepository.deleteById(id);
    }

    private InvoiceDto convertToDto(Invoice invoice) {
        InvoiceDto dto = new InvoiceDto();
        dto.setId(invoice.getId());
        dto.setInvoiceNumber(invoice.getInvoiceNumber());
        dto.setAmount(invoice.getAmount());
        dto.setIssueDate(invoice.getIssueDate());
        dto.setStatus(invoice.getStatus());
        dto.setRejectionNote(invoice.getRejectionNote());
        if (invoice.getPayments() != null && !invoice.getPayments().isEmpty()) {
            dto.setPaymentId(invoice.getPayments().get(0).getId());
        }
        if (invoice.getPurchaseOrder() != null) {
            PurchaseOrder po = invoice.getPurchaseOrder();
            dto.setPurchaseOrderId(po.getId());
            dto.setPoNumber(po.getId().toString().substring(0, 8).toUpperCase());
            dto.setRequiredDate(po.getRequiredDate());
            if (po.getVendor() != null) {
                dto.setVendorName(po.getVendor().getCompanyName());
                dto.setVendorEmail(po.getVendor().getEmail());
            }
            if (po.getItems() != null) {
                if (!po.getItems().isEmpty()) {
                    dto.setDescriptionSummary(po.getItems().get(0).getProduct().getName() + 
                        (po.getItems().size() > 1 ? " and others" : ""));
                }
                dto.setTotalQuantity(po.getItems().stream().mapToInt(PurchaseOrderItem::getQuantity).sum());
                
                dto.setItems(po.getItems().stream().map(item -> {
                    com.vms.dto.PurchaseOrderItemDto itemDto = new com.vms.dto.PurchaseOrderItemDto();
                    itemDto.setId(item.getId());
                    if (item.getProduct() != null) {
                        itemDto.setProductId(item.getProduct().getId());
                        itemDto.setProductName(item.getProduct().getName());
                        itemDto.setProductDescription(item.getProduct().getDescription());
                    }
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setPrice(item.getPrice());
                    return itemDto;
                }).collect(Collectors.toList()));
            }
        }
        return dto;
    }

    private void updateEntity(Invoice invoice, InvoiceDto dto) {
        invoice.setAmount(dto.getAmount());
        invoice.setIssueDate(dto.getIssueDate());
        invoice.setInvoiceNumber(dto.getInvoiceNumber());
        invoice.setStatus(dto.getStatus());
        if (dto.getPurchaseOrderId() != null) {
            PurchaseOrder po = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Purchase Order not found with id: " + dto.getPurchaseOrderId()));
            invoice.setPurchaseOrder(po);
        }
    }
}
