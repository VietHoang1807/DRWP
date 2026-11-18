package vn.drwp.platform.tenancy;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hai "mặt phẳng" dữ liệu:
 * <ul>
 *   <li>Primary ({@code dataSource}, {@code jdbcTemplate}, {@code jdbcClient}, {@code transactionManager}):
 *       định tuyến theo tenant, dùng cho dữ liệu nghiệp vụ, event publication registry và outbox.</li>
 *   <li>{@code @Qualifier("controlPlane")}: schema {@code platform} (tenant registry/placement/membership, ShedLock).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenancyProperties.class)
public class TenancyConfiguration {

    public static final String CONTROL_PLANE = "controlPlane";

    @Bean(destroyMethod = "close")
    ClusterDataSources clusterDataSources(TenancyProperties properties) {
        return new ClusterDataSources(properties);
    }

    @Bean
    NoTenantSchemaInitializer noTenantSchemaInitializer(ClusterDataSources clusters) {
        return new NoTenantSchemaInitializer(clusters);
    }

    @Bean
    PlacementCache placementCache(ObjectProvider<TenantPlacementLookup> lookup, TenancyProperties properties) {
        return new PlacementCache(lookup, properties.placementCacheTtl());
    }

    @Bean
    TenancyStartupGuard tenancyStartupGuard(TenancyProperties properties) {
        return new TenancyStartupGuard(properties.strict());
    }

    /** {@code noTenantSchema} chỉ để bảo đảm schema no-tenant được tạo trước khi DataSource chính được dùng. */
    @Bean
    @Primary
    DataSource dataSource(ClusterDataSources clusters, PlacementCache placements, TenancyStartupGuard guard,
                          NoTenantSchemaInitializer noTenantSchema) {
        return new TenantRoutingDataSource(clusters, placements, guard);
    }

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
        return JdbcClient.create(jdbcTemplate);
    }

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @Primary
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @Qualifier(CONTROL_PLANE)
    DataSource controlPlaneDataSource(ClusterDataSources clusters) {
        return clusters.controlPlane();
    }

    @Bean
    @Qualifier(CONTROL_PLANE)
    JdbcTemplate controlPlaneJdbcTemplate(@Qualifier(CONTROL_PLANE) DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    @Qualifier(CONTROL_PLANE)
    JdbcClient controlPlaneJdbcClient(@Qualifier(CONTROL_PLANE) JdbcTemplate jdbcTemplate) {
        return JdbcClient.create(jdbcTemplate);
    }

    @Bean
    @Qualifier(CONTROL_PLANE)
    PlatformTransactionManager controlPlaneTransactionManager(@Qualifier(CONTROL_PLANE) DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    @Qualifier(CONTROL_PLANE)
    TransactionTemplate controlPlaneTransactionTemplate(@Qualifier(CONTROL_PLANE) PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }

    @Bean
    TenantSchemaModule platformSchemaModule() {
        return new TenantSchemaModule("platform", 0);
    }

    @Bean
    TenantSchemaMigrator tenantSchemaMigrator(ClusterDataSources clusters, List<TenantSchemaModule> modules) {
        return new TenantSchemaMigrator(clusters, modules);
    }

    @Bean
    TaskDecorator tenantContextTaskDecorator() {
        return new TenantContextTaskDecorator();
    }

    @Bean
    TenantJobRunner tenantJobRunner(ObjectProvider<TenantDirectory> directory) {
        return new TenantJobRunner(directory);
    }
}
