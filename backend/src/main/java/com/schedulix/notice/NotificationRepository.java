package com.schedulix.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);

    long countByUserAccountIdAndReadAtIsNull(UUID userAccountId);

    Optional<Notification> findByIdAndUserAccountId(UUID id, UUID userAccountId);

    List<Notification> findByUserAccountIdAndReadAtIsNull(UUID userAccountId);
}