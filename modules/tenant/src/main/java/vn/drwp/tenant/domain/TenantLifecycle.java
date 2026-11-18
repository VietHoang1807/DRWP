package vn.drwp.tenant.domain;

import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.tenancy.TenantStatus;

/** Luật chuyển trạng thái tenant. */
public final class TenantLifecycle {

    private TenantLifecycle() {
    }

    public static void requireTransition(TenantStatus from, TenantStatus to) {
        boolean allowed = switch (from) {
            case PROVISIONING -> to == TenantStatus.ACTIVE;
            case ACTIVE -> to == TenantStatus.SUSPENDED;
            case SUSPENDED -> to == TenantStatus.ACTIVE || to == TenantStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new InvalidStateException("Tenant cannot move from " + from + " to " + to);
        }
    }
}
