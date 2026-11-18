package vn.drwp.platform.messaging;

import java.util.Map;

import vn.drwp.platform.kernel.tenant.TenantId;

/** Sự kiện externalized đã qua kiểm tra provenance (ADR 022). */
public record ExternalEvent(
        String messageId,
        String type,
        int version,
        TenantId tenantId,
        String aggregateType,
        String aggregateId,
        Map<String, Object> payload) {
}
