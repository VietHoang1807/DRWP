package vn.drwp.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class IdentitySchemaModule {

    @Bean
    TenantSchemaModule identityTenantSchema() {
        return new TenantSchemaModule("identity", 10);
    }
}
