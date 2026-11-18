package vn.drwp.metadata.api;

import java.time.Instant;
import java.util.UUID;

import vn.drwp.platform.kernel.event.TenantScopedEvent;

/** Sự kiện nội bộ khi phát hành phiên bản biểu mẫu. */
public record FormPublished(String tenantId, String formCode, UUID formVersionId, int versionNo, String publishedBy,
                            Instant publishedAt) implements TenantScopedEvent {
}
