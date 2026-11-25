package com.schedulix.notice;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository notificationRepository, CurrentUser currentUser) {
        this.notificationRepository = notificationRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public void send(UUID userId, String text, String link) {
        notificationRepository.save(new Notification(userId, text, link));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Page<NotificationResponse> result = notificationRepository
                .findByUserAccountIdOrderByCreatedAtDesc(currentUser.userId(), PageRequest.of(safePage, safeSize))
                .map(NotificationResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserAccountIdAndReadAtIsNull(currentUser.userId());
    }

    @Transactional
    public void markRead(UUID id) {
        Notification notification = notificationRepository
                .findByIdAndUserAccountId(id, currentUser.userId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "This notification was not found."
                ));
        notification.markRead(Instant.now());
    }

    @Transactional
    public void markAllRead() {
        Instant now = Instant.now();
        List<Notification> unread = notificationRepository.findByUserAccountIdAndReadAtIsNull(currentUser.userId());
        for (Notification notification : unread) {
            notification.markRead(now);
        }
    }
}