package com.vms.dto;

import com.vms.entity.Status;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ContractDto {
    private UUID id;
    private UUID vendorId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal contractValue;
    private Status status;
}
