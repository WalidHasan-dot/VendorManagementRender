package com.vms.service;

import com.vms.dto.PageResponse;
import com.vms.dto.PaymentDto;
import com.vms.dto.PayslipDto;
import com.vms.entity.Invoice;
import com.vms.entity.Payment;
import com.vms.entity.Status;
import com.vms.entity.User;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.InvoiceRepository;
import com.vms.repository.PaymentRepository;
import com.vms.repository.UserRepository;
import com.vms.repository.VendorRepository;

import com.vms.service.NotificationService;
import com.vms.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private NotificationService notificationService;

    public List<PaymentDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public PageResponse<PaymentDto> getAllPaymentsPaginated(Pageable pageable) {
        Page<Payment> page = paymentRepository.findAll(pageable);
        List<PaymentDto> content = page.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public PaymentDto getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return convertToDto(payment);
    }

    public List<PaymentDto> getPaymentsForVendor(UUID userId) {
        var vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for current user"));
        return paymentRepository.findByInvoice_PurchaseOrder_VendorId(vendor.getId()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public PaymentDto getPaymentByIdForContext(UUID id, UUID userId) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        var vendorOpt = vendorRepository.findByUserId(userId);
        if (vendorOpt.isPresent()) {
            if (payment.getInvoice() == null || payment.getInvoice().getPurchaseOrder() == null
                    || payment.getInvoice().getPurchaseOrder().getVendor() == null
                    || !payment.getInvoice().getPurchaseOrder().getVendor().getId().equals(vendorOpt.get().getId())) {
                throw new ResourceNotFoundException("Payment not accessible for current vendor");
            }
        }
        return convertToDto(payment);
    }

    public PaymentDto createPayment(PaymentDto dto) {
        Payment payment = new Payment();
        updateEntity(payment, dto);

        if (payment.getInvoice() != null) {
            Invoice invoice = payment.getInvoice();
            invoice.setStatus(Status.PAID);
            invoiceRepository.save(invoice);
        }

        payment = paymentRepository.save(payment);
        User currentUser = getCurrentUser();
        auditService.log(currentUser, "CREATE_PAYMENT", "PAYMENT", payment.getId().toString(), "Payment created");
        if (payment.getInvoice() != null && payment.getInvoice().getPurchaseOrder() != null) {
            var po = payment.getInvoice().getPurchaseOrder();
            if (po.getCreatedBy() != null) {
                notificationService.createNotification(
                        po.getCreatedBy(),
                        "Payment completed for your order: #" + po.getId().toString().substring(0, 8));
            }
            if (po.getVendor() != null && po.getVendor().getUser() != null) {
                notificationService.createNotification(
                        po.getVendor().getUser(),
                        "Payment completed for order: #" + po.getId().toString().substring(0, 8));
            }
        }
        notificationService.notifyAdmins("Payment completed for order: #" + (payment.getInvoice() != null && payment.getInvoice().getPurchaseOrder() != null ? payment.getInvoice().getPurchaseOrder().getId().toString().substring(0, 8) : ""));
        return convertToDto(payment);
    }

    public PaymentDto updatePayment(UUID id, PaymentDto dto) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        updateEntity(payment, dto);
        payment = paymentRepository.save(payment);
        User currentUser = getCurrentUser();
        auditService.log(currentUser, "UPDATE_PAYMENT_STATUS", "PAYMENT", payment.getId().toString(), "Payment updated");
        return convertToDto(payment);
    }

    public PaymentDto markAsPaid(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        User currentUser = getCurrentUser();
        if (currentUser == null) throw new IllegalStateException("Authentication required");

        // If user is VENDOR, check if it's their invoice
        var vendorOpt = vendorRepository.findByUserId(currentUser.getId());
        if (vendorOpt.isPresent()) {
            if (invoice.getPurchaseOrder() == null || invoice.getPurchaseOrder().getVendor() == null
                    || !invoice.getPurchaseOrder().getVendor().getId().equals(vendorOpt.get().getId())) {
                throw new IllegalStateException("You can only mark your own invoices as paid");
            }
        }

        if (invoice.getStatus() == Status.PAID) {
            throw new IllegalStateException("Invoice is already paid");
        }

        invoice.setStatus(Status.PAID);
        invoiceRepository.save(invoice);

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(invoice.getAmount());
        payment.setPaymentDate(java.time.LocalDateTime.now());
        payment.setMethod("BANK_TRANSFER");
        payment = paymentRepository.save(payment);

        auditService.log(currentUser, "MARK_PAID", "PAYMENT", payment.getId().toString(), "Payment marked as PAID, PAYSLIP generated");

        if (invoice.getPurchaseOrder() != null && invoice.getPurchaseOrder().getVendor() != null && invoice.getPurchaseOrder().getVendor().getUser() != null) {
            notificationService.createNotification(
                    invoice.getPurchaseOrder().getVendor().getUser(),
                    "Your invoice for PO #" + invoice.getPurchaseOrder().getId().toString().substring(0, 8) + " has been marked as PAID.");
        }

        return convertToDto(payment);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            return userRepository.findById(((UserDetailsImpl) auth.getPrincipal()).getId()).orElse(null);
        }
        return null;
    }

    public void deletePayment(UUID id) {
        paymentRepository.deleteById(id);
    }

    public PayslipDto getPayslip(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        Invoice invoice = payment.getInvoice();
        if (invoice == null || invoice.getPurchaseOrder() == null
                || invoice.getPurchaseOrder().getVendor() == null) {
            throw new IllegalStateException("Payment is not fully linked to invoice/vendor");
        }

        var po = invoice.getPurchaseOrder();
        var vendor = po != null ? po.getVendor() : null;

        if (vendor == null) {
            throw new IllegalStateException("Payment is not fully linked to a vendor");
        }

        PayslipDto dto = new PayslipDto();
        dto.setVendorId(vendor.getId());
        dto.setVendorName(vendor.getCompanyName());
        dto.setInvoiceId(invoice.getId());
        dto.setPurchaseOrderId(po.getId());
        dto.setPaymentAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setPaymentMethod(payment.getMethod());

        String baseRef = payment.getId().toString().substring(0, 8).toUpperCase();
        dto.setTransactionReference("TX-" + baseRef);
        dto.setPayslipReference("PS-" + baseRef);

        if (po.getItems() != null) {
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

        return dto;
    }

    private PaymentDto convertToDto(Payment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setMethod(payment.getMethod());
        dto.setPaymentStatus("PAID");
        if (payment.getInvoice() != null) {
            dto.setInvoiceId(payment.getInvoice().getId());
            if (payment.getInvoice().getPurchaseOrder() != null) {
                dto.setPurchaseOrderId(payment.getInvoice().getPurchaseOrder().getId().toString());
                if (payment.getInvoice().getPurchaseOrder().getVendor() != null) {
                    dto.setVendorName(payment.getInvoice().getPurchaseOrder().getVendor().getCompanyName());
                }
            }
        }
        return dto;
    }

    private void updateEntity(Payment payment, PaymentDto dto) {
        payment.setAmount(dto.getAmount());
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setMethod(dto.getMethod());
        if (dto.getInvoiceId() != null) {
            Invoice invoice = invoiceRepository.findById(dto.getInvoiceId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Invoice not found with id: " + dto.getInvoiceId()));
            payment.setInvoice(invoice);
        }
    }
}
