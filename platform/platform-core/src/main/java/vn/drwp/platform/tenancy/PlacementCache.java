package vn.drwp.platform.tenancy;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.tenant.TenantId;

/** Cache ngắn hạn cho placement; module Tenant gọi {@link #evict} khi đổi trạng thái/placement. */
public class PlacementCache {

    private final ObjectProvider<TenantPlacementLookup> lookup;
    private final Cache<TenantId, TenantPlacement> cache;

    public PlacementCache(ObjectProvider<TenantPlacementLookup> lookup, Duration ttl) {
        this.lookup = lookup;
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
    }

    /**
     * Placement đang dùng được: tenant ACTIVE, hoặc PROVISIONING khi thread đang provisioning.
     */
    public TenantPlacement requireUsable(TenantId tenantId, boolean provisioning) {
        TenantPlacement p = provisioning ? load(tenantId) : cache.get(tenantId, this::load);
        if (p.tenantStatus() == TenantStatus.ACTIVE
                || (provisioning && p.tenantStatus() == TenantStatus.PROVISIONING)) {
            return p;
        }
        cache.invalidate(tenantId);
        throw new TenantIsolationException("Tenant " + tenantId + " is not active (" + p.tenantStatus() + ")");
    }

    public Optional<TenantPlacement> peek(TenantId tenantId) {
        return lookup.getObject().find(tenantId);
    }

    public void evict(TenantId tenantId) {
        cache.invalidate(tenantId);
    }

    private TenantPlacement load(TenantId tenantId) {
        return lookup.getObject().find(tenantId)
                .orElseThrow(() -> new TenantIsolationException("No placement for tenant " + tenantId));
    }
}
