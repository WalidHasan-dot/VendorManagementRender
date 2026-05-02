package com.vms.controller;

import com.vms.dto.PageResponse;
import com.vms.dto.VendorDto;
import com.vms.dto.VendorRatingDto;
import com.vms.service.VendorService;
import com.vms.service.VendorRatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/vendors")
@lombok.extern.slf4j.Slf4j
public class VendorController {

    @Autowired
    private VendorService vendorService;

    @Autowired
    private VendorRatingService vendorRatingService;

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public List<VendorDto> getAllVendors() {
        List<VendorDto> vendors = vendorService.getAllVendors();
        log.info("Fetching all vendors. Count: {}", vendors.size());
        return vendors;
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<?> getVendorsPage(
            @RequestParam(required = false) String companyName,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        PageResponse<VendorDto> page = vendorService.getAllVendorsPaginated(companyName, pageable);
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Vendors fetched", page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('FINANCE') or hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<VendorDto> getVendorById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(vendorService.getVendorById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorDto> createVendor(@RequestBody VendorDto vendorDto) {
        return ResponseEntity.ok(vendorService.createVendor(vendorDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorDto> updateVendor(@PathVariable("id") UUID id, @RequestBody VendorDto vendorDto) {
        return ResponseEntity.ok(vendorService.updateVendor(id, vendorDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteVendor(@PathVariable("id") UUID id) {
        log.info("REST request to delete vendor with ID: {}", id);
        vendorService.deleteVendor(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorDto> updateMyProfile(@RequestBody VendorDto vendorDto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        return ResponseEntity.ok(vendorService.updateOwnProfile(userDetails.getId(), vendorDto));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveVendor(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        vendorService.approveVendor(id, admin);
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Vendor approved successfully"));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectVendor(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        vendorService.rejectVendor(id, admin);
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Vendor rejected successfully"));
    }

    @PostMapping("/{id}/ratings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> rateVendor(@PathVariable UUID id,
            @RequestBody VendorRatingDto ratingDto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Rating saved",
                vendorRatingService.rateVendor(id, ratingDto, userDetails.getId())));
    }

    @GetMapping("/{id}/ratings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasRole('FINANCE') or hasRole('VENDOR')")
    public ResponseEntity<?> getVendorRatings(@PathVariable UUID id) {
        return ResponseEntity.ok(com.vms.dto.ApiResponse.success("Ratings fetched",
                vendorRatingService.getRatingsForVendor(id)));
    }
}
