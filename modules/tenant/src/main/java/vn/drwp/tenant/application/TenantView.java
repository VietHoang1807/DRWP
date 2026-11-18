package vn.drwp.tenant.application;

import java.time.Instant;
import java.util.List;

import vn.drwp.platform.tenancy.TenantStatus;

public record TenantView(
        String tenantId,
        String displayName,
        TenantStatus status,
        List<String> complianceProfiles,
        String clusterId,
        String schemaName,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
