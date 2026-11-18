package vn.drwp.tenant.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import vn.drwp.tenant.application.TenantProvisioningService;

/** Áp migration mới cho mọi tenant ACTIVE khi khởi động ({@code drwp.tenancy.migrate-on-startup}). */
@Component
@ConditionalOnProperty(prefix = "drwp.tenancy", name = "migrate-on-startup", havingValue = "true", matchIfMissing = true)
class TenantStartupMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantStartupMigration.class);

    private final TenantProvisioningService provisioning;

    TenantStartupMigration(TenantProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = provisioning.migrateAllActive();
        log.info("Tenant schema migration checked for {} active tenant(s)", migrated);
    }
}
