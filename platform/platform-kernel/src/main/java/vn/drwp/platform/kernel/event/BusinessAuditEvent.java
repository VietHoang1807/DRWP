package vn.drwp.platform.kernel.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Sự kiện audit nghiệp vụ dùng chung cho mọi module (dependency matrix: Audit &lt;- All, Event).
 * Đặt ở shared kernel để module Audit không phụ thuộc vào từng module phát.
 * {@code detail} chỉ chứa dữ liệu tối thiểu, không chứa PII/token (tài liệu 02 mục 4).
 */
public record BusinessAuditEvent(
        String tenantId,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        Outcome outcome,
        Map<String, String> detail,
        String correlationId,
        Instant occurredAt) implements TenantScopedEvent {

    public enum Outcome { SUCCESS, DENIED, FAILED }

    public BusinessAuditEvent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        detail = detail == null ? Map.of() : Map.copyOf(detail);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
