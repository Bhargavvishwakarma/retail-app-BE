package com.retail.notification.controller;

import com.retail.dto.PageResponse;
import com.retail.notification.dto.NotificationCountResponse;
import com.retail.notification.dto.NotificationResponse;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.realtime.NotificationStreamService;
import com.retail.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/** Recipient-owned notification history and authenticated real-time stream endpoints. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    @GetMapping
    public PageResponse<NotificationResponse> history(Authentication authentication,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestParam(required = false) NotificationCategory category,
                                                       @RequestParam(required = false) NotificationSeverity severity,
                                                       @RequestParam(required = false) Boolean isRead) {
        return notificationService.history(authentication.getName(), normalizedPage(page), normalizedSize(size),
                category, severity, isRead);
    }

    @GetMapping("/unread-count")
    public NotificationCountResponse unreadCount(Authentication authentication) {
        return notificationService.unreadCount(authentication.getName());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(Authentication authentication, @PathVariable UUID id) {
        return notificationService.markRead(authentication.getName(), id);
    }

    @PatchMapping("/read-all")
    public NotificationCountResponse markAllRead(Authentication authentication) {
        return notificationService.markAllRead(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        notificationService.delete(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/read")
    public NotificationCountResponse deleteRead(Authentication authentication) {
        return notificationService.deleteRead(authentication.getName());
    }

    /**
     * The JWT filter has already authenticated this request; native EventSource
     * clients cannot attach a bearer header, so the browser should use a
     * fetch-based SSE client or an EventSource polyfill that can do so.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return streamService.connect(notificationService.currentUserId(authentication.getName()), lastEventId);
    }

    private int normalizedPage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int normalizedSize(Integer size) {
        return size == null ? 20 : Math.min(100, Math.max(1, size));
    }
}
