package com.vms.service;

import com.vms.dto.PageResponse;
import com.vms.entity.Notification;
import com.vms.entity.User;
import com.vms.repository.NotificationRepository;
import com.vms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void createNotification(User user, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    public void notifyAdmins(String message) {
        List<User> admins = userRepository.findByUserRole("ADMIN");
        for (User admin : admins) {
            createNotification(admin, message);
        }
    }

    public void notifyFinance(String message) {
        List<User> finance = userRepository.findByUserRole("FINANCE");
        for (User f : finance) {
            createNotification(f, message);
        }
    }

    public List<Notification> getNotificationsForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public PageResponse<Notification> getNotificationsForUserPaginated(User user, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public void markAsReadForUser(Long id, User user) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser() != null && n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Autowired
    private UserRepository userRepository;
}
