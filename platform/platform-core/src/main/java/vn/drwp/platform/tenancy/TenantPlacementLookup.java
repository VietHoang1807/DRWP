package vn.drwp.platform.tenancy;

import java.util.Optional;

import vn.drwp.platform.kernel.tenant.TenantId;

/** SPI do module Tenant cài đặt (đọc control-plane). */
public interface TenantPlacementLookup {

    Optional<TenantPlacement> find(TenantId tenantId);

    /** Tra tenant từ schema nguồn (ADR 022: bằng chứng tenant của sự kiện Kafka). */
    Optional<TenantPlacement> findBySchema(String clusterId, String schemaName);
}
