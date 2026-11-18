package vn.drwp.workflow.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

public record WorkflowCompleted(String tenantId, UUID instanceId, UUID recordId, String recordCode, String outcome,
                                String recordOwner, String comment, Instant completedAt) implements TenantScopedEvent {
}
