package vn.drwp.platform.messaging;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenancyConfiguration;

/** ADR 018: vai trò scheduled-worker; ShedLock trên control-plane để mỗi job chỉ chạy trên một node. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRuntimeRole(RuntimeRole.SCHEDULED_WORKER)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfiguration {

    @Bean
    LockProvider lockProvider(@Qualifier(TenancyConfiguration.CONTROL_PLANE) JdbcTemplate controlPlane) {
        return new JdbcTemplateLockProvider(controlPlane, "shedlock");
    }
}
