package vn.drwp.tenant.infrastructure;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenantStatus;
import vn.drwp.platform.web.security.CurrentActor;
import vn.drwp.tenant.application.ProvisionTenantCommand;
import vn.drwp.tenant.application.TenantProvisioningService;
import vn.drwp.tenant.application.TenantQueryService;
import vn.drwp.tenant.application.TenantView;

/** Quản trị nền tảng: chỉ realm role {@code platform-admin} (SecurityConfiguration chặn /api/platform/**). */
@RestController
@RequestMapping("/api/platform/tenants")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class PlatformTenantController {

    private final TenantProvisioningService provisioning;
    private final TenantQueryService queries;

    PlatformTenantController(TenantProvisioningService provisioning, TenantQueryService queries) {
        this.provisioning = provisioning;
        this.queries = queries;
    }

    record CreateTenantRequest(@NotBlank String tenantId, @NotBlank @Size(max = 200) String displayName,
                               String clusterId, List<String> complianceProfiles,
                               @NotBlank String adminSubject, String adminDisplayName) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantView create(@Valid @RequestBody CreateTenantRequest r) {
        return provisioning.provision(new ProvisionTenantCommand(r.tenantId(), r.displayName(), r.clusterId(),
                r.complianceProfiles(), r.adminSubject(), r.adminDisplayName()), CurrentActor.require().subject());
    }

    @GetMapping
    List<TenantView> list() {
        return queries.all();
    }

    @GetMapping("/{tenantId}")
    TenantView get(@PathVariable String tenantId) {
        return queries.get(TenantId.of(tenantId));
    }

    @PostMapping("/{tenantId}/suspend")
    TenantView suspend(@PathVariable String tenantId) {
        return provisioning.changeStatus(TenantId.of(tenantId), TenantStatus.SUSPENDED, CurrentActor.require().subject());
    }

    @PostMapping("/{tenantId}/activate")
    TenantView activate(@PathVariable String tenantId) {
        return provisioning.changeStatus(TenantId.of(tenantId), TenantStatus.ACTIVE, CurrentActor.require().subject());
    }

    @PostMapping("/{tenantId}/migrate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void migrate(@PathVariable String tenantId) {
        provisioning.migrate(TenantId.of(tenantId), CurrentActor.require().subject());
    }
}
