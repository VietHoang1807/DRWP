package vn.drwp.platform.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import vn.drwp.platform.json.Json;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {

    @Bean
    TenantEventGuardAspect tenantEventGuardAspect() {
        return new TenantEventGuardAspect();
    }

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbcClient, Json json) {
        return new OutboxWriter(jdbcClient, json);
    }

    @Bean
    InboxStore inboxStore(JdbcClient jdbcClient) {
        return new InboxStore(jdbcClient);
    }
}
