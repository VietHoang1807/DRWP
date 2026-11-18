package vn.drwp.record.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class RecordSchemaModule {

    @Bean
    TenantSchemaModule recordTenantSchema() {
        return new TenantSchemaModule("record", 30);
    }
}
