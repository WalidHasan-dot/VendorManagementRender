package com.vms.service;

import com.vms.dto.VendorRatingDto;
import com.vms.entity.PurchaseOrder;
import com.vms.entity.Status;
import com.vms.entity.User;
import com.vms.entity.Vendor;
import com.vms.entity.VendorRating;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.PurchaseOrderRepository;
import com.vms.repository.UserRepository;
import com.vms.repository.VendorRatingRepository;
import com.vms.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vms.service.NotificationService;

@Service
@Transactional
public class VendorRatingService {

    @Autowired
    private VendorRatingRepository vendorRatingRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    public VendorRatingDto rateVendor(UUID vendorId, VendorRatingDto dto, UUID userId) {
        if (dto.getRating() < 1 || dto.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + vendorId));

        PurchaseOrder po = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        if (!userId.equals(po.getCreatedBy().getId()) || !vendorId.equals(po.getVendor().getId())) {
            throw new IllegalStateException("You can only rate vendors for your own completed orders");
        }
        if (po.getStatus() != Status.COMPLETED && po.getStatus() != Status.DELIVERED) {
            throw new IllegalStateException("Only COMPLETED or DELIVERED orders can be rated");
        }

        if (vendorRatingRepository.existsByPurchaseOrderIdAndUserId(po.getId(), userId)) {
            throw new IllegalStateException("You have already rated this order");
        }

        VendorRating rating = new VendorRating();
        rating.setVendor(vendor);
        rating.setUser(user);
        rating.setPurchaseOrder(po);
        rating.setRating(dto.getRating());
        rating.setComment(dto.getComment());

        rating = vendorRatingRepository.save(rating);

        int newTotal = (vendor.getTotalRatings() != null ? vendor.getTotalRatings() : 0) + 1;
        double oldAverage = vendor.getAverageRating() != null ? vendor.getAverageRating() : 0.0;
        double newAverage = ((oldAverage * (newTotal - 1)) + dto.getRating()) / newTotal;

        vendor.setTotalRatings(newTotal);
        vendor.setAverageRating(newAverage);
        vendorRepository.save(vendor);

        if (vendor.getUser() != null) {
            notificationService.createNotification(
                    vendor.getUser(),
                    "You have received a new rating of " + dto.getRating() + " stars from user " + user.getUsername() + ".");
        }

        VendorRatingDto result = new VendorRatingDto();
        result.setId(rating.getId());
        result.setVendorId(vendorId);
        result.setPurchaseOrderId(po.getId());
        result.setRating(rating.getRating());
        result.setComment(rating.getComment());
        result.setCreatedBy(user.getUsername());
        result.setCreatedAt(rating.getCreatedAt());
        return result;
    }

    public List<com.vms.dto.PurchaseOrderDto> getCompletedOrdersForRating(UUID userId) {
        List<Status> eligibleStatuses = List.of(Status.COMPLETED, Status.DELIVERED);
        return purchaseOrderRepository.findByCreatedByIdAndStatusIn(userId, eligibleStatuses).stream()
                .filter(po -> !vendorRatingRepository.existsByPurchaseOrderIdAndUserId(po.getId(), userId))
                .map(this::convertToPoDto)
                .collect(Collectors.toList());
    }

    private com.vms.dto.PurchaseOrderDto convertToPoDto(PurchaseOrder po) {
        com.vms.dto.PurchaseOrderDto dto = new com.vms.dto.PurchaseOrderDto();
        dto.setId(po.getId());
        dto.setVendorId(po.getVendor().getId());
        dto.setVendorName(po.getVendor().getCompanyName());
        dto.setStatus(po.getStatus().name());
        dto.setTotalAmount(po.getTotalAmount());
        dto.setCreatedAt(po.getCreatedAt());
        // Add items if needed, but for listing orders for rating simple DTO is enough
        return dto;
    }

    public List<VendorRatingDto> getRatingsForVendor(UUID vendorId) {
        return vendorRatingRepository.findByVendorId(vendorId).stream().map(r -> {
            VendorRatingDto dto = new VendorRatingDto();
            dto.setId(r.getId());
            dto.setVendorId(vendorId);
            dto.setPurchaseOrderId(r.getPurchaseOrder().getId());
            dto.setRating(r.getRating());
            dto.setComment(r.getComment());
            dto.setCreatedAt(r.getCreatedAt());
            dto.setCreatedBy(r.getUser().getUsername());
            return dto;
        }).collect(Collectors.toList());
    }
}

