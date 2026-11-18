package vn.drwp.platform.tenancy;

import vn.drwp.platform.kernel.tenant.TenantId;

/** SPI do module Tenant cài đặt: principal có membership ACTIVE trong tenant ACTIVE hay không. */
public interface TenantMembershipVerifier {
    boolean isActiveMember(TenantId tenantId, String subject);
}
