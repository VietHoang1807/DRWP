package vn.drwp.tenant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.ClusterDataSources;
import vn.drwp.platform.tenancy.PlacementCache;
import vn.drwp.platform.tenancy.TenancyConfiguration;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.tenancy.TenantPlacement;
import vn.drwp.platform.tenancy.TenantSchemaMigrator;
import vn.drwp.platform.tenancy.TenantStatus;
import vn.drwp.tenant.api.TenantInitializer;
import vn.drwp.tenant.domain.ComplianceProfile;
import vn.drwp.tenant.domain.TenantLifecycle;
import vn.drwp.tenant.infrastructure.ControlPlaneRepository;

/**
 * Provisioning tenant: registry + placement -> migration schema -> initializer (trong tenant context)
 * -> membership admin -> ACTIVE. Lỗi giữa chừng để lại PROVISIONING + last_error; gọi lại sẽ tiếp tục.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final ControlPlaneRepository repository;
    private final TransactionTemplate controlPlaneTx;
    private final TransactionTemplate tenantTx;
    private final TenantSchemaMigrator migrator;
    private final ClusterDataSources clusters;
    private final PlacementCache placementCache;
    private final List<TenantInitializer> initializers;

    public TenantProvisioningService(ControlPlaneRepository repository,
                                     @Qualifier(TenancyConfiguration.CONTROL_PLANE) TransactionTemplate controlPlaneTx,
                                     TransactionTemplate tenantTx,
                                     TenantSchemaMigrator migrator,
                                     ClusterDataSources clusters,
                                     PlacementCache placementCache,
                                     List<TenantInitializer> initializers) {
        this.repository = repository;
        this.controlPlaneTx = controlPlaneTx;
        this.tenantTx = tenantTx;
        this.migrator = migrator;
        this.clusters = clusters;
        this.placementCache = placementCache;
        this.initializers = initializers;
    }

    public TenantView provision(ProvisionTenantCommand cmd, String actor) {
        TenantId tenantId = TenantId.of(cmd.tenantId());
        if (cmd.adminSubject() == null || cmd.adminSubject().isBlank()) {
            throw new IllegalArgumentException("adminSubject is required");
        }
        String cluster = cmd.clusterId() == null || cmd.clusterId().isBlank() ? clusters.defaultCluster() : cmd.clusterId();
        if (!clusters.hasCluster(cluster)) {
            throw new IllegalArgumentException("Unknown cluster: " + cluster);
        }
        List<String> profiles = normalizeProfiles(cmd.complianceProfiles());

        TenantView existing = repository.find(tenantId).orElse(null);
        if (existing != null && existing.status() != TenantStatus.PROVISIONING) {
            throw new ConflictException("TENANT_EXISTS", "Tenant already exists: " + tenantId);
        }
        if (existing == null) {
            controlPlaneTx.executeWithoutResult(s -> {
                repository.insertTenant(tenantId, cmd.displayName(), profiles, cluster, tenantId.defaultSchemaName());
                repository.audit(actor, "TENANT_PROVISION_STARTED", tenantId,
                        Map.of("cluster", cluster, "profiles", profiles));
            });
        }

        TenantPlacement placement = placementCache.peek(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant placement", tenantId));
        try {
            migrator.migrate(placement);
            TenantContext.runProvisioning(tenantId, () -> tenantTx.executeWithoutResult(s ->
                    initializers.forEach(i -> i.initialize(tenantId, cmd.adminSubject()))));
            controlPlaneTx.executeWithoutResult(s -> {
                repository.upsertMember(tenantId, cmd.adminSubject(), cmd.adminDisplayName(), actor);
                TenantLifecycle.requireTransition(TenantStatus.PROVISIONING, TenantStatus.ACTIVE);
                repository.updateStatus(tenantId, TenantStatus.PROVISIONING, TenantStatus.ACTIVE, null);
                repository.audit(actor, "TENANT_ACTIVATED", tenantId,
                        Map.of("schema", placement.schemaName(), "admin", cmd.adminSubject()));
            });
        } catch (RuntimeException e) {
            log.error("Provisioning failed for tenant {}", tenantId, e);
            repository.recordError(tenantId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            placementCache.evict(tenantId);
        }
        return repository.find(tenantId).orElseThrow();
    }

    public TenantView changeStatus(TenantId tenantId, TenantStatus target, String actor) {
        TenantView current = repository.find(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        TenantLifecycle.requireTransition(current.status(), target);
        controlPlaneTx.executeWithoutResult(s -> {
            if (repository.updateStatus(tenantId, current.status(), target, null) != 1) {
                throw new ConflictException("CONCURRENT_UPDATE", "Tenant status changed concurrently");
            }
            repository.audit(actor, "TENANT_" + target.name(), tenantId, Map.of("from", current.status().name()));
        });
        placementCache.evict(tenantId);
        return repository.find(tenantId).orElseThrow();
    }

    /** Chạy lại migration cho một tenant (ví dụ sau khi phát hành phiên bản mới). */
    public void migrate(TenantId tenantId, String actor) {
        TenantPlacement placement = placementCache.peek(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        migrator.migrate(placement);
        repository.audit(actor, "TENANT_MIGRATED", tenantId, Map.of("schema", placement.schemaName()));
    }

    /** Migration cho mọi tenant ACTIVE (lúc khởi động, Flyway khóa theo schema nên chạy song song nhiều node an toàn). */
    public int migrateAllActive() {
        int count = 0;
        for (TenantPlacement p : repository.placementsByStatus(TenantStatus.ACTIVE)) {
            migrator.migrate(p);
            count++;
        }
        return count;
    }

    private static List<String> normalizeProfiles(List<String> requested) {
        List<String> result = new ArrayList<>();
        result.add(ComplianceProfile.COMMERCIAL.name()); // baseline luôn có
        if (requested != null) {
            for (String p : requested) {
                String name = ComplianceProfile.valueOf(p.trim().toUpperCase(Locale.ROOT)).name();
                if (!result.contains(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }
}
