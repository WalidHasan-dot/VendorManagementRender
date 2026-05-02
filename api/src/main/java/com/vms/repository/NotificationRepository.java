package com.vms.repository;

import com.vms.entity.Notification;
import com.vms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    List<Notification> findByUserOrderByCreatedAtDesc(com.vms.entity.User user);

    long countByUserAndIsReadFalse(com.vms.entity.User user);

    java.util.List<Notification> findTop10ByUserOrderByCreatedAtDesc(User user);

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
