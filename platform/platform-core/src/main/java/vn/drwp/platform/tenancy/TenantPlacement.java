package vn.drwp.platform.tenancy;

import java.util.Objects;

import vn.drwp.platform.kernel.tenant.TenantId;

/** Vị trí dữ liệu của tenant (bảng {@code tenant_placement}). */
public record TenantPlacement(
        TenantId tenantId,
        String clusterId,
        String databaseId,
        String schemaName,
        long placementVersion,
        TenantStatus tenantStatus) {

    public TenantPlacement {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(clusterId);
        SchemaNames.requireValid(schemaName);
    }
}
