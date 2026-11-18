package vn.drwp.audit.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class AuditSchemaModule {

    @Bean
    TenantSchemaModule auditTenantSchema() {
        return new TenantSchemaModule("audit", 70);
    }
}
