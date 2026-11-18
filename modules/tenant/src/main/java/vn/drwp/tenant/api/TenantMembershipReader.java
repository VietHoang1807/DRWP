package vn.drwp.tenant.api;

import java.util.List;

import vn.drwp.platform.kernel.tenant.TenantId;

/** Contract cho module Identity (dependency matrix: Identity -> Tenant, Direct). */
public interface TenantMembershipReader {

    boolean isActiveMember(TenantId tenantId, String subject);

    List<MemberView> members(TenantId tenantId);

    record MemberView(String subject, String displayName, String status) {
    }
}
