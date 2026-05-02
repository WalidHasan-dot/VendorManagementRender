package com.vms.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ProductDto {
    private UUID id;
    private UUID vendorId;
    private String vendorName;
    private String name;
    private String description;
    private Double price;
}
