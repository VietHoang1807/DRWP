package vn.drwp.tenant.application;

import java.util.List;

import org.springframework.stereotype.Service;

import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.tenant.infrastructure.ControlPlaneRepository;

@Service
public class TenantQueryService {

    private final ControlPlaneRepository repository;

    public TenantQueryService(ControlPlaneRepository repository) {
        this.repository = repository;
    }

    public List<TenantView> all() {
        return repository.list();
    }

    public TenantView get(TenantId tenantId) {
        return repository.find(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    public List<TenantView> tenantsOf(String subject) {
        return repository.tenantsOfSubject(subject);
    }
}
