package vn.drwp.record.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

public record RecordStatusChanged(String tenantId, UUID recordId, String recordCode, String formCode,
                                  RecordStatus from, RecordStatus to, String owner, String actor,
                                  Instant occurredAt) implements TenantScopedEvent {
}
