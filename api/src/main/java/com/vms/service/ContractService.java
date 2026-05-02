package com.vms.service;

import com.vms.dto.ContractDto;
import com.vms.entity.Contract;
import com.vms.entity.Vendor;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.ContractRepository;
import com.vms.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContractService {

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private VendorRepository vendorRepository;

    public List<ContractDto> getAllContracts() {
        return contractRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public ContractDto getContractById(UUID id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        return convertToDto(contract);
    }

    public ContractDto createContract(ContractDto dto) {
        Contract contract = new Contract();
        updateEntity(contract, dto);
        return convertToDto(contractRepository.save(contract));
    }

    public ContractDto updateContract(UUID id, ContractDto dto) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        updateEntity(contract, dto);
        return convertToDto(contractRepository.save(contract));
    }

    public void deleteContract(UUID id) {
        contractRepository.deleteById(id);
    }

    private ContractDto convertToDto(Contract contract) {
        ContractDto dto = new ContractDto();
        dto.setId(contract.getId());
        dto.setStartDate(contract.getStartDate());
        dto.setEndDate(contract.getEndDate());
        dto.setContractValue(contract.getContractValue());
        dto.setStatus(contract.getStatus());
        if (contract.getVendor() != null) {
            dto.setVendorId(contract.getVendor().getId());
        }
        return dto;
    }

    private void updateEntity(Contract contract, ContractDto dto) {
        contract.setStartDate(dto.getStartDate());
        contract.setEndDate(dto.getEndDate());
        contract.setContractValue(dto.getContractValue());
        contract.setStatus(dto.getStatus());
        if (dto.getVendorId() != null) {
            Vendor vendor = vendorRepository.findById(dto.getVendorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found with id: " + dto.getVendorId()));
            contract.setVendor(vendor);
        }
    }
}
