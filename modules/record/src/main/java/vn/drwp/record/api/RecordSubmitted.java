package vn.drwp.record.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

/**
 * ADR 016: Record phát sự kiện này khi nộp; Workflow nghe bằng @ApplicationModuleListener (sau commit,
 * transaction riêng, registry phát lại nếu lỗi). {@code submissionId} giúp Workflow idempotent khi phát lại.
 */
public record RecordSubmitted(String tenantId, UUID recordId, String recordCode, String formCode, UUID formVersionId,
                              String submittedBy, UUID submissionId, Instant submittedAt) implements TenantScopedEvent {
}
