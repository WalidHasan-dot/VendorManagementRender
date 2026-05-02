package com.vms.controller;

import com.vms.dto.UserDto;
import com.vms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveUser(@PathVariable("id") UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        if (id == null) {
            return ResponseEntity.badRequest()
                    .body(com.vms.dto.ApiResponse.error("Operational Error: Missing identity reference."));
        }

        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        userService.approveUser(id, admin);
        return ResponseEntity
                .ok(com.vms.dto.ApiResponse.success("Administrative Protocol: Identity approved successfully."));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectUser(@PathVariable("id") UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        if (id == null) {
            return ResponseEntity.badRequest()
                    .body(com.vms.dto.ApiResponse.error("Operational Error: Missing identity reference."));
        }

        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        userService.rejectUser(id, admin);
        return ResponseEntity
                .ok(com.vms.dto.ApiResponse.success("Administrative Protocol: Identity request rejected."));
    }
}

