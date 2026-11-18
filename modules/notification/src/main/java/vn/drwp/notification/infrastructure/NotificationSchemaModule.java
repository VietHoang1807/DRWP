package vn.drwp.notification.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.drwp.platform.tenancy.TenantSchemaModule;

@Configuration(proxyBeanMethods = false)
class NotificationSchemaModule {

    @Bean
    TenantSchemaModule notificationTenantSchema() {
        return new TenantSchemaModule("notification", 60);
    }
}
