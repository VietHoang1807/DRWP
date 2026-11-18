package vn.drwp.workflow.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class WorkflowSchemaModule {

    @Bean
    TenantSchemaModule workflowTenantSchema() {
        return new TenantSchemaModule("workflow", 40);
    }
}
