package vn.drwp.identity.application;

import java.util.List;

import org.springframework.stereotype.Component;

import vn.drwp.identity.domain.Grant;
import vn.drwp.identity.domain.RoleCode;
import vn.drwp.identity.infrastructure.IdentityRepository;
import vn.drwp.platform.kernel.security.PermissionMask;
import vn.drwp.platform.kernel.security.ResourceCode;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.tenant.api.TenantInitializer;

/** Khởi tạo vai trò hệ thống TENANT_ADMIN (mọi quyền trên {@code *}) và gán cho admin đầu tiên. Idempotent. */
@Component
class IdentityTenantInitializer implements TenantInitializer {

    private final IdentityRepository repository;

    IdentityTenantInitializer(IdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public void initialize(TenantId tenantId, String adminSubject) {
        var roleId = repository.insertRoleIfAbsent(RoleCode.TENANT_ADMIN, "Quản trị đơn vị",
                "Vai trò hệ thống: toàn quyền trong tenant", true);
        repository.replaceGrants(roleId, List.of(new Grant(ResourceCode.WILDCARD, PermissionMask.ALL_DEFINED, 0)));
        repository.assign(adminSubject, roleId, "system:provisioning");
        repository.bumpPolicyVersion();
    }
}
