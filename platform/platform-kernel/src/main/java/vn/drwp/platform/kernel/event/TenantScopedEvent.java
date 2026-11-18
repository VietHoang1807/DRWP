package vn.drwp.platform.kernel.event;

/**
 * Mọi sự kiện nội bộ mang {@code tenantId}; listener từ chối nếu lệch tenant context (ADR 021).
 */
public interface TenantScopedEvent {
    String tenantId();
}
