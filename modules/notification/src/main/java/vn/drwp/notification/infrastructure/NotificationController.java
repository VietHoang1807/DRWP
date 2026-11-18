package vn.drwp.notification.infrastructure;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.drwp.notification.application.NotificationService;
import vn.drwp.notification.application.NotificationService.NotificationView;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.PageResponse;
import vn.drwp.platform.web.security.CurrentActor;

@RestController
@RequestMapping("/api/notifications")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        var p = notifications.list(CurrentActor.require(), unreadOnly, PageResponse.clampPage(page), PageResponse.clampSize(size));
        return new PageResponse<>(p.items(), p.page(), p.size(), p.total());
    }

    @GetMapping("/unread-count")
    Map<String, Long> unread() {
        return Map.of("unread", notifications.unreadCount(CurrentActor.require()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable UUID id) {
        notifications.markRead(CurrentActor.require(), id);
    }

    @PostMapping("/read-all")
    Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead(CurrentActor.require()));
    }
}
