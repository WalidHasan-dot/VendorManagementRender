package com.vms.service;

import com.vms.dto.UserDto;
import com.vms.entity.Status;
import com.vms.entity.User;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.UserRepository;
import com.vms.repository.AuditLogRepository;
import com.vms.repository.NotificationRepository;
import com.vms.repository.VendorRepository;
import com.vms.repository.RefreshTokenRepository;
import com.vms.repository.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private NotificationService notificationService;

        @Autowired
        private AuditService auditService;

        @Autowired
        private AuditLogRepository auditLogRepository;

        @Autowired
        private NotificationRepository notificationRepository;

        @Autowired
        private VendorRepository vendorRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private PurchaseOrderRepository purchaseOrderRepository;

        public List<UserDto> getAllUsers() {
                return userRepository.findAll().stream()
                                .map(this::convertToDto)
                                .collect(Collectors.toList());
        }

        public List<UserDto> getPendingUsers() {
                return userRepository.findByStatus(Status.PENDING).stream()
                                .map(this::convertToDto)
                                .collect(Collectors.toList());
        }

        public void approveUser(UUID id, User adminDetail) {
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User identity not found in registry"));

                User admin = userRepository.findById(adminDetail.getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Administrative identity not verified"));

                user.setStatus(Status.ACTIVE);
                userRepository.save(user);

                notificationService.createNotification(user,
                                "Administrative Protocol: Your account has been officially APPROVED.");
                auditService.log(admin, "USER_APPROVAL", "Identity approved for system access: " + user.getUsername());
        }

        public void rejectUser(UUID id, User adminDetail) {
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User identity not found in registry"));

                User admin = userRepository.findById(adminDetail.getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Administrative identity not verified"));

        user.setStatus(Status.DENIED);
        userRepository.save(user);

        notificationService.createNotification(user,
                                "Administrative Protocol: Your account request has been DENIED.");
                auditService.log(admin, "USER_REJECTION", "Identity access request revoked: " + user.getUsername());
        }
        public User getUserById(UUID id) {
                return userRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        }
        public void deleteUser(UUID id, User adminDetail) {
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Identity record not found: " + id));

                User admin = userRepository.findById(adminDetail.getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Administrative authorization failure"));

                if (user.getId().equals(admin.getId())) {
                        throw new RuntimeException(
                                        "Operational Constraint: Cannot purge the active administrative identity.");
                }

                // 1. Clear Security Sessions (Refresh Tokens)
                refreshTokenRepository.deleteByUser(user);

                // 2. Clear System Directives (Notifications & Audit Logs)
                notificationRepository.findByUserId(id).forEach(n -> notificationRepository.delete(n));
                auditLogRepository.findByUserId(id).forEach(l -> auditLogRepository.delete(l));

                // 3. Dissociate Supply Entities (Vendors)
                vendorRepository.findByUserId(id).ifPresent(v -> {
                        v.setUser(null);
                        vendorRepository.save(v);
                });

                // 4. Resolve Transactional Entities (Purchase Orders)
                // Note: This will cascade to Invoices and Payments due to entity configuration
                purchaseOrderRepository.findByCreatedById(id).forEach(po -> purchaseOrderRepository.delete(po));

                // 5. Final Registry Purge
                userRepository.delete(user);
                auditService.log(admin, "USER_DELETION",
                                "Identity successfully purged from system: " + user.getUsername());
        }

        private UserDto convertToDto(User user) {
                if (user == null)
                        return null;
                UserDto dto = new UserDto();
                dto.setId(user.getId());
                dto.setUsername(user.getUsername());
                dto.setEmail(user.getEmail());
                dto.setStatus(user.getStatus() != null ? user.getStatus().name() : null);
                if (user.getRoles() != null) {
                        dto.setRoles(user.getRoles().stream()
                                        .filter(role -> role != null && role.getName() != null)
                                        .map(role -> role.getName().name())
                                        .collect(Collectors.toSet()));
                }
                dto.setUserRole(user.getUserRole());
                return dto;
        }
}
