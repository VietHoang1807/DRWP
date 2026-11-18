package vn.drwp.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.tenant.TenantId;

class TenantContextTest {

    private static final TenantId A = TenantId.of("alpha");
    private static final TenantId B = TenantId.of("beta");

    @Test
    void bindsAndClearsInFinally() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(() -> TenantContext.runAs(A, () -> {
            assertThat(TenantContext.require()).isEqualTo(A);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void refusesToSwitchTenantOnSameThread() {
        TenantContext.runAs(A, () -> {
            TenantContext.runAs(A, () -> assertThat(TenantContext.require()).isEqualTo(A)); // cùng tenant: được
            assertThatThrownBy(() -> TenantContext.runAs(B, () -> { }))
                    .isInstanceOf(TenantIsolationException.class);
            assertThat(TenantContext.require()).isEqualTo(A);
        });
    }

    @Test
    void requireFailsClosed() {
        assertThatThrownBy(TenantContext::require).isInstanceOf(TenantIsolationException.class);
    }

    @Test
    void taskDecoratorPropagatesAndCleansUp() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();
            AtomicReference<TenantId> seen = new AtomicReference<>();
            Runnable decorated = TenantContext.callAs(A, () -> decorator.decorate(() -> seen.set(TenantContext.require())));
            CompletableFuture.runAsync(decorated, pool).get();
            assertThat(seen.get()).isEqualTo(A);
            // thread của pool không còn giữ tenant sau khi task kết thúc
            AtomicReference<Boolean> leaked = new AtomicReference<>();
            CompletableFuture.runAsync(() -> leaked.set(TenantContext.current().isPresent()), pool).get();
            assertThat(leaked.get()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void interleavedTenantsDoNotLeak() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();
            var futures = new java.util.ArrayList<CompletableFuture<Boolean>>();
            for (int i = 0; i < 200; i++) {
                TenantId t = i % 2 == 0 ? A : B;
                CompletableFuture<Boolean> f = new CompletableFuture<>();
                Runnable r = TenantContext.callAs(t, () -> decorator.decorate(() -> f.complete(TenantContext.require().equals(t))));
                pool.execute(r);
                futures.add(f);
            }
            for (var f : futures) {
                assertThat(f.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
