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
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> getPendingUsers() {
        return userService.getPendingUsers();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveUser(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        if (id == null)
            return ResponseEntity.badRequest()
                    .body(com.vms.dto.ApiResponse.error("Operational Error: Missing identity reference."));

        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        userService.approveUser(id, admin);
        return ResponseEntity
                .ok(com.vms.dto.ApiResponse.success("Administrative Protocol: Identity approved successfully."));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectUser(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        if (id == null)
            return ResponseEntity.badRequest()
                    .body(com.vms.dto.ApiResponse.error("Operational Error: Missing identity reference."));

        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        userService.rejectUser(id, admin);
        return ResponseEntity
                .ok(com.vms.dto.ApiResponse.success("Administrative Protocol: Identity request rejected."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.vms.security.UserDetailsImpl userDetails) {
        if (id == null)
            return ResponseEntity.badRequest()
                    .body(com.vms.dto.ApiResponse.error("Operational Error: Missing identity reference."));

        com.vms.entity.User admin = new com.vms.entity.User();
        admin.setId(userDetails.getId());
        userService.deleteUser(id, admin);
        return ResponseEntity.ok(com.vms.dto.ApiResponse
                .success("Administrative Protocol: Identity purged from directory successfully."));
    }
}
