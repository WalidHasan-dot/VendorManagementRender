package com.vms.controller;

import com.vms.entity.VendorCategory;
import com.vms.repository.VendorCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/vendor-categories")
public class VendorCategoryController {

    @Autowired
    private VendorCategoryRepository vendorCategoryRepository;

    @GetMapping
    public List<VendorCategory> getAll() {
        return vendorCategoryRepository.findAll();
    }
}
