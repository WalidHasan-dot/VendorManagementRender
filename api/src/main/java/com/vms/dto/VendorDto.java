package com.vms.dto;

import com.vms.entity.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorDto {
    private UUID id;
    private String companyName;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private Status status;
    private String category;
    private Long categoryId;
    /** Username for vendor login (when admin creates vendor). If provided, creates User with VENDOR role. */
    private String username;
    /** Password for vendor login (when admin creates vendor). Required if username is provided. */
    private String password;
    /** Average rating from user reviews (1-5 stars) */
    private Double averageRating = 0.0;
    /** Total number of ratings received */
    private Integer totalRatings = 0;
}
