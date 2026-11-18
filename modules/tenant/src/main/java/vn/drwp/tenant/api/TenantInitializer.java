package vn.drwp.tenant.api;

import vn.drwp.platform.kernel.tenant.TenantId;

/**
 * SPI chạy trong lúc provisioning, sau migration, trong tenant context (provisioning) và transaction của tenant.
 * Phải idempotent vì provisioning có thể được chạy lại khi lỗi giữa chừng.
 */
public interface TenantInitializer {

    void initialize(TenantId tenantId, String adminSubject);
}
