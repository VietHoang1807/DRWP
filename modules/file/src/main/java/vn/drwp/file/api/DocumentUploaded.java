package vn.drwp.file.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

/** Dependency matrix: AI Processing -> File (Event). Phase 7 sẽ nghe sự kiện này (qua outbox) để OCR. */
public record DocumentUploaded(String tenantId, UUID fileId, UUID recordId, String contentType, long sizeBytes,
                               String uploadedBy, Instant uploadedAt) implements TenantScopedEvent {
}
