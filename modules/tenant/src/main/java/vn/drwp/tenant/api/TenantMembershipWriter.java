package vn.drwp.tenant.api;

import vn.drwp.platform.kernel.tenant.TenantId;

public interface TenantMembershipWriter {

    /** Thêm hoặc kích hoạt lại membership. */
    void addMember(TenantId tenantId, String subject, String displayName, String addedBy);

    void suspendMember(TenantId tenantId, String subject, String changedBy);
}
