package vn.drwp.metadata.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class MetadataSchemaModule {

    @Bean
    TenantSchemaModule metadataTenantSchema() {
        return new TenantSchemaModule("metadata", 20);
    }
}
