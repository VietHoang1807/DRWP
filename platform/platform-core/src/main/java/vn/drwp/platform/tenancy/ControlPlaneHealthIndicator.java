package vn.drwp.platform.tenancy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Health của control-plane. Health DB mặc định bị tắt vì DataSource chính cần tenant context. */
@Component("controlPlane")
class ControlPlaneHealthIndicator extends AbstractHealthIndicator {

    private final JdbcClient jdbc;
    private final ClusterDataSources clusters;

    ControlPlaneHealthIndicator(@Qualifier(TenancyConfiguration.CONTROL_PLANE) JdbcClient jdbc, ClusterDataSources clusters) {
        this.jdbc = jdbc;
        this.clusters = clusters;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Integer one = jdbc.sql("SELECT 1").query(Integer.class).single();
        builder.up().withDetail("controlPlane", one == 1 ? "ok" : "unexpected")
                .withDetail("clusters", clusters.tenantPools().keySet());
    }
}
