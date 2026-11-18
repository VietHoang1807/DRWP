package vn.drwp.tenant.infrastructure;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.security.CurrentActor;
import vn.drwp.tenant.application.TenantQueryService;

/** Danh sách tenant mà người dùng hiện tại là thành viên (để client chọn tenant). */
@RestController
@ConditionalOnRuntimeRole(RuntimeRole.API)
class MyTenantsController {

    private final TenantQueryService queries;

    MyTenantsController(TenantQueryService queries) {
        this.queries = queries;
    }

    record MyTenant(String tenantId, String displayName) {
    }

    @GetMapping("/api/me/tenants")
    List<MyTenant> myTenants() {
        return queries.tenantsOf(CurrentActor.require().subject()).stream()
                .map(t -> new MyTenant(t.tenantId(), t.displayName()))
                .toList();
    }
}
