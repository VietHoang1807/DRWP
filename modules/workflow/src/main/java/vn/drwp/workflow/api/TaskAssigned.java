package vn.drwp.workflow.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

/** Sự kiện nội bộ cho Notification (dependency matrix: Notification -> Workflow, Event). */
public record TaskAssigned(String tenantId, UUID taskId, UUID instanceId, UUID recordId, String recordCode,
                           String taskName, String assigneeType, String assigneeValue, Instant dueAt)
        implements TenantScopedEvent {
}
