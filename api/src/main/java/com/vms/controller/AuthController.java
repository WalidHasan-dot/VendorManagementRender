package com.vms.controller;

import com.vms.dto.JwtResponse;
import com.vms.dto.LoginRequest;
import com.vms.dto.MessageResponse;
import com.vms.dto.RegisterRequest;
import com.vms.entity.Role;
import com.vms.entity.RoleName;
import com.vms.entity.User;
import com.vms.repository.RoleRepository;
import com.vms.repository.UserRepository;
import com.vms.security.JwtUtils;
import com.vms.security.UserDetailsImpl;
import com.vms.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    com.vms.service.VendorService vendorService;

    @Autowired
    com.vms.service.RefreshTokenService refreshTokenService;

    @Autowired
    com.vms.service.AuditService auditService;

    @Autowired
    com.vms.repository.VendorRepository vendorRepository;

    @Autowired
    com.vms.service.NotificationService notificationService;

    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody com.vms.dto.TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(com.vms.entity.RefreshToken::getUser)
                .map(user -> {
                    com.vms.entity.Vendor vendor = vendorRepository.findByUser(user).orElse(null);
                    java.util.UUID vendorId = vendor != null ? vendor.getId() : null;
                    java.util.List<String> roles = user.getRoles().stream()
                            .map(role -> "ROLE_" + role.getName().name())
                            .collect(Collectors.toList());
                    String token = jwtUtils.generateTokenFromUsernameAndClaims(user.getUsername(), user.getId(), vendorId, roles);
                    return ResponseEntity.ok(new com.vms.dto.TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/vendor-register")
    public ResponseEntity<?> registerVendor(@Valid @RequestBody com.vms.dto.VendorRegisterRequest registerRequest) {
        try {
            vendorService.registerVendor(registerRequest);
            return ResponseEntity.ok(ApiResponse
                    .success("Vendor registered successfully! Admin will review your application."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            System.out.println("DEBUG: Attempting login for user: " + loginRequest.getUsername());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            System.out.println("DEBUG: User authenticated successfully: " + userDetails.getUsername() + ", Status: "
                    + userDetails.isEnabled());

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            com.vms.entity.RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

            User user = userRepository.findById(userDetails.getId()).orElse(null);
            auditService.log(user, "USER_LOGIN", "USER", userDetails.getId().toString(), "User logged in successfully");

            return ResponseEntity.ok(new JwtResponse(jwt,
                    refreshToken.getToken(),
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    userDetails.getVendorId(),
                    roles));
        } catch (AuthenticationException e) {
            System.out.println("DEBUG: Authentication failed: " + e.getMessage());
            if (e instanceof DisabledException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Account is pending admin approval."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));
        } catch (Exception e) {
            System.out.println("DEBUG: Unexpected error during signin: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An internal server error occurred during login"));
        }
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
            User user = userRepository.findById(userDetails.getId()).orElse(null);
            auditService.log(user, "USER_LOGOUT", "USER", userDetails.getId().toString(), "User logged out");
            refreshTokenService.deleteByUserId(userDetails.getId());
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        Set<String> strRoles = signUpRequest.getRole();
        String requestedRole = signUpRequest.getUserRole();
        if ((requestedRole == null || requestedRole.isBlank()) && strRoles != null && !strRoles.isEmpty()) {
            requestedRole = strRoles.iterator().next();
        }
        if (requestedRole == null || requestedRole.isBlank()) {
            requestedRole = "USER";
        }
        requestedRole = requestedRole.toUpperCase();
        if (!requestedRole.equals("ADMIN") && !requestedRole.equals("FINANCE") && !requestedRole.equals("USER")) {
            requestedRole = "USER";
        }

        // Create new user's account
        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));

        Set<Role> roles = new HashSet<>();
        RoleName roleName = RoleName.USER;
        if ("ADMIN".equals(requestedRole)) {
            roleName = RoleName.ADMIN;
        } else if ("FINANCE".equals(requestedRole)) {
            roleName = RoleName.FINANCE;
        }
        roles.add(roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found.")));

        user.setRoles(roles);
        user.setUserRole(requestedRole);

        if ("USER".equals(requestedRole)) {
            user.setStatus(com.vms.entity.Status.PENDING);
        } else {
            user.setStatus(com.vms.entity.Status.ACTIVE);
        }

        userRepository.save(user);

        // Audit & Notifications
        auditService.log(user, "USER_REGISTRATION", "New user registered with role: " + requestedRole);
        if ("USER".equals(requestedRole)) {
            notificationService.notifyAdmins(
                    "New user registration pending approval: " + user.getUsername());
        }

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
