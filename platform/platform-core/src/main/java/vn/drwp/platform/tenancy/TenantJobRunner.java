package vn.drwp.platform.tenancy;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import vn.drwp.platform.kernel.tenant.TenantId;

/**
 * Lặp qua tenant ACTIVE, đặt tenant context cho từng tenant, dọn trong finally (ADR 019).
 * Lỗi ở một tenant không chặn tenant khác.
 */
public class TenantJobRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantJobRunner.class);

    private final ObjectProvider<TenantDirectory> directory;

    public TenantJobRunner(ObjectProvider<TenantDirectory> directory) {
        this.directory = directory;
    }

    public int forEachActiveTenant(String jobName, Consumer<TenantId> action) {
        int failures = 0;
        for (TenantId tenant : directory.getObject().activeTenants()) {
            try {
                TenantContext.runAs(tenant, () -> action.accept(tenant));
            } catch (RuntimeException e) {
                failures++;
                log.error("Job {} failed for tenant {}", jobName, tenant, e);
            }
        }
        return failures;
    }
}
