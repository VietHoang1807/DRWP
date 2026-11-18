package vn.drwp.notification.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.api.RoleDirectory;
import vn.drwp.notification.infrastructure.NotificationRepository;
import vn.drwp.notification.infrastructure.NotificationRepository.NotificationRow;
import vn.drwp.platform.messaging.OutboxWriter;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.workflow.api.TaskAssigned;
import vn.drwp.workflow.api.WorkflowCompleted;

/**
 * Tạo thông báo từ sự kiện nội bộ (Notification -> Workflow, Event). Phát {@code NotificationCreated} qua outbox để
 * gateway đẩy WebSocket (ADR 013) khi gateway được triển khai.
 */
@Service
public class NotificationService {

    public record NotificationView(UUID id, String type, String title, String body, String refType, String refId,
                                   boolean read, java.time.Instant createdAt) {
        static NotificationView of(NotificationRow r) {
            return new NotificationView(r.id(), r.type(), r.title(), r.body(), r.refType(), r.refId(), r.readAt() != null,
                    r.createdAt());
        }
    }

    public record Page(List<NotificationView> items, int page, int size, long total) {
    }

    private final NotificationRepository repository;
    private final RoleDirectory roles;
    private final OutboxWriter outbox;

    NotificationService(NotificationRepository repository, RoleDirectory roles, OutboxWriter outbox) {
        this.repository = repository;
        this.roles = roles;
        this.outbox = outbox;
    }

    @ApplicationModuleListener
    public void onTaskAssigned(TaskAssigned e) {
        List<String> recipients = "USER".equals(e.assigneeType()) ? List.of(e.assigneeValue()) : roles.subjectsInRole(e.assigneeValue());
        String title = "Công việc mới: " + e.taskName() + " (" + e.recordCode() + ")";
        String body = e.dueAt() == null ? null : "Hạn xử lý: " + e.dueAt();
        for (String r : recipients) {
            notify(r, "TASK_ASSIGNED", title, body, "task", e.taskId().toString());
        }
    }

    @ApplicationModuleListener
    public void onWorkflowCompleted(WorkflowCompleted e) {
        String title = switch (e.outcome()) {
            case "APPROVED" -> "Hồ sơ " + e.recordCode() + " đã được phê duyệt";
            case "REJECTED" -> "Hồ sơ " + e.recordCode() + " bị từ chối";
            case "RETURNED" -> "Hồ sơ " + e.recordCode() + " được trả lại để bổ sung";
            default -> "Hồ sơ " + e.recordCode() + " đã kết thúc (" + e.outcome() + ")";
        };
        notify(e.recordOwner(), "WORKFLOW_" + e.outcome(), title, e.comment(), "record", e.recordId().toString());
    }

    private void notify(String recipient, String type, String title, String body, String refType, String refId) {
        UUID id = repository.insertIfAbsent(recipient, type, title, body, refType, refId);
        if (id != null) {
            outbox.append("notification", id.toString(), "NotificationCreated", 1,
                    Map.of("notificationId", id.toString(), "recipient", recipient, "type", type));
        }
    }

    @Transactional(readOnly = true)
    public Page list(Actor actor, boolean unreadOnly, int page, int size) {
        return new Page(repository.list(actor.subject(), unreadOnly, size, (long) page * size).stream()
                .map(NotificationView::of).toList(), page, size, repository.count(actor.subject(), unreadOnly));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Actor actor) {
        return repository.count(actor.subject(), true);
    }

    @Transactional
    public void markRead(Actor actor, UUID id) {
        repository.markRead(id, actor.subject());
    }

    @Transactional
    public int markAllRead(Actor actor) {
        return repository.markAllRead(actor.subject());
    }
}
