package vn.drwp.platform.tenancy;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.MDC;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.tenant.TenantId;

/**
 * Tenant context theo thread. Không cho phép đổi sang tenant khác khi đang ở trong một tenant
 * (tài liệu 01 mục 4: "không đổi tenant giữa transaction"). Luôn dọn trong {@code finally}.
 */
public final class TenantContext {

    public static final String MDC_KEY = "tenant";

    /** Trạng thái chụp được để truyền sang thread khác (ADR 021). */
    public record Snapshot(TenantId tenantId, boolean provisioning) {
    }

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantId> current() {
        Snapshot s = CURRENT.get();
        return s == null ? Optional.empty() : Optional.of(s.tenantId());
    }

    public static TenantId require() {
        return current().orElseThrow(() -> new TenantIsolationException("No tenant context bound to current thread"));
    }

    public static boolean isProvisioning() {
        Snapshot s = CURRENT.get();
        return s != null && s.provisioning();
    }

    public static Snapshot snapshot() {
        return CURRENT.get();
    }

    public static void runAs(TenantId tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callAs(TenantId tenantId, Supplier<T> action) {
        return callWith(new Snapshot(tenantId, false), action);
    }

    /** Chỉ dùng trong quy trình provisioning: cho phép truy cập schema của tenant đang PROVISIONING. */
    public static void runProvisioning(TenantId tenantId, Runnable action) {
        callWith(new Snapshot(tenantId, true), () -> {
            action.run();
            return null;
        });
    }

    /** Khôi phục snapshot (dùng cho TaskDecorator). snapshot null = chạy không tenant. */
    public static <T> T callWith(Snapshot snapshot, Supplier<T> action) {
        Snapshot previous = CURRENT.get();
        if (snapshot != null && previous != null && !previous.tenantId().equals(snapshot.tenantId())) {
            throw new TenantIsolationException("Refusing to switch tenant from " + previous.tenantId()
                    + " to " + snapshot.tenantId() + " on the same thread");
        }
        set(snapshot);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    private static void set(Snapshot snapshot) {
        if (snapshot == null) {
            CURRENT.remove();
            MDC.remove(MDC_KEY);
        } else {
            CURRENT.set(snapshot);
            MDC.put(MDC_KEY, snapshot.tenantId().value());
        }
    }
}
