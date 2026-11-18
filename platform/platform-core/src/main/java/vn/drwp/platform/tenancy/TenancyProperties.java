package vn.drwp.platform.tenancy;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình cụm PostgreSQL và control-plane.
 *
 * <pre>
 * drwp.tenancy:
 *   control-plane: { cluster: cluster-a, schema: platform, pool-size: 5 }
 *   default-cluster: cluster-a
 *   clusters:
 *     cluster-a: { url: jdbc:postgresql://..., username: ..., password: ..., pool-size: 20 }
 * </pre>
 */
@ConfigurationProperties("drwp.tenancy")
public record TenancyProperties(
        ControlPlane controlPlane,
        String defaultCluster,
        Map<String, Cluster> clusters,
        @DefaultValue("30s") Duration placementCacheTtl,
        @DefaultValue("true") boolean strict,
        @DefaultValue("true") boolean migrateOnStartup) {

    public record ControlPlane(String cluster, @DefaultValue("platform") String schema, @DefaultValue("5") int poolSize) {
    }

    public record Cluster(String url, String username, String password, @DefaultValue("20") int poolSize) {
    }
}
