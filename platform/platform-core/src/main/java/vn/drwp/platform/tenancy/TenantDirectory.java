package vn.drwp.platform.tenancy;

import java.util.List;

import vn.drwp.platform.kernel.tenant.TenantId;

/** SPI do module Tenant cài đặt: liệt kê tenant ACTIVE cho job định kỳ. */
public interface TenantDirectory {
    List<TenantId> activeTenants();
}
