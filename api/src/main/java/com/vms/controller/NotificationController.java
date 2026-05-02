package com.vms.controller;

import com.vms.dto.ApiResponse;
import com.vms.entity.User;
import com.vms.security.UserDetailsImpl;
import com.vms.service.NotificationService;
import com.vms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/my")
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity
                .ok(ApiResponse.success("Notifications fetched", notificationService.getNotificationsForUser(user)));
    }

    @GetMapping("/my/page")
    public ResponseEntity<?> getMyNotificationsPage(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched",
                notificationService.getNotificationsForUserPaginated(user, pageable)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        notificationService.markAsReadForUser(id, user);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched", notificationService.countUnread(user)));
    }
}
