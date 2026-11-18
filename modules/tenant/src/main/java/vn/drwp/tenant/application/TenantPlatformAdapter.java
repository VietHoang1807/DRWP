package vn.drwp.tenant.application;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.TenancyConfiguration;
import vn.drwp.platform.tenancy.TenantDirectory;
import vn.drwp.platform.tenancy.TenantMembershipVerifier;
import vn.drwp.platform.tenancy.TenantPlacement;
import vn.drwp.platform.tenancy.TenantPlacementLookup;
import vn.drwp.platform.tenancy.TenantStatus;
import vn.drwp.tenant.api.TenantMembershipReader;
import vn.drwp.tenant.api.TenantMembershipWriter;
import vn.drwp.tenant.infrastructure.ControlPlaneRepository;

/** Cài đặt các SPI platform và public contract của module Tenant trên control-plane. */
@Component
class TenantPlatformAdapter implements TenantPlacementLookup, TenantDirectory, TenantMembershipVerifier,
        TenantMembershipReader, TenantMembershipWriter {

    private final ControlPlaneRepository repository;
    private final TransactionTemplate controlPlaneTx;

    TenantPlatformAdapter(ControlPlaneRepository repository,
                          @Qualifier(TenancyConfiguration.CONTROL_PLANE) TransactionTemplate controlPlaneTx) {
        this.repository = repository;
        this.controlPlaneTx = controlPlaneTx;
    }

    @Override
    public Optional<TenantPlacement> find(TenantId tenantId) {
        return repository.placement(tenantId);
    }

    @Override
    public Optional<TenantPlacement> findBySchema(String clusterId, String schemaName) {
        if (clusterId == null || schemaName == null) {
            return Optional.empty();
        }
        return repository.placementBySchema(clusterId, schemaName);
    }

    @Override
    public List<TenantId> activeTenants() {
        return repository.placementsByStatus(TenantStatus.ACTIVE).stream().map(TenantPlacement::tenantId).toList();
    }

    @Override
    public boolean isActiveMember(TenantId tenantId, String subject) {
        return subject != null && repository.isActiveMember(tenantId, subject);
    }

    @Override
    public List<MemberView> members(TenantId tenantId) {
        return repository.members(tenantId);
    }

    @Override
    public void addMember(TenantId tenantId, String subject, String displayName, String addedBy) {
        controlPlaneTx.executeWithoutResult(s -> {
            repository.upsertMember(tenantId, subject, displayName, addedBy);
            repository.audit(addedBy, "MEMBER_ADDED", tenantId, java.util.Map.of("subject", subject));
        });
    }

    @Override
    public void suspendMember(TenantId tenantId, String subject, String changedBy) {
        controlPlaneTx.executeWithoutResult(s -> {
            if (repository.suspendMember(tenantId, subject) == 0) {
                throw new vn.drwp.platform.kernel.error.NotFoundException("Member", subject);
            }
            repository.audit(changedBy, "MEMBER_SUSPENDED", tenantId, java.util.Map.of("subject", subject));
        });
    }
}
