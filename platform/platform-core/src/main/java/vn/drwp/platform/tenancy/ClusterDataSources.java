package vn.drwp.platform.tenancy;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Một pool Hikari cho mỗi cụm PostgreSQL (tenant placement có thể ở cụm khác nhau) và một pool
 * riêng cho control-plane. Pool tenant có schema mặc định không tồn tại ({@link SchemaNames#NO_TENANT_SCHEMA}),
 * Hikari tự reset schema về giá trị này khi connection trả về pool.
 */
public class ClusterDataSources implements AutoCloseable {

    private final Map<String, HikariDataSource> tenantPools = new LinkedHashMap<>();
    private final HikariDataSource controlPlane;
    private final String defaultCluster;

    public ClusterDataSources(TenancyProperties props) {
        if (props.clusters() == null || props.clusters().isEmpty()) {
            throw new IllegalStateException("drwp.tenancy.clusters must define at least one cluster");
        }
        props.clusters().forEach((id, c) -> tenantPools.put(id, pool("drwp-tenant-" + id, c, SchemaNames.NO_TENANT_SCHEMA, c.poolSize())));
        TenancyProperties.ControlPlane cp = props.controlPlane();
        TenancyProperties.Cluster cpCluster = props.clusters().get(cp.cluster());
        if (cpCluster == null) {
            throw new IllegalStateException("Control-plane cluster not configured: " + cp.cluster());
        }
        this.controlPlane = pool("drwp-control-plane", cpCluster, cp.schema(), cp.poolSize());
        this.defaultCluster = props.defaultCluster() != null ? props.defaultCluster() : cp.cluster();
        if (!tenantPools.containsKey(defaultCluster)) {
            throw new IllegalStateException("Default cluster not configured: " + defaultCluster);
        }
    }

    private static HikariDataSource pool(String name, TenancyProperties.Cluster c, String schema, int size) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName(name);
        cfg.setJdbcUrl(c.url());
        cfg.setUsername(c.username());
        cfg.setPassword(c.password());
        cfg.setMaximumPoolSize(size);
        cfg.setSchema(schema);
        cfg.setAutoCommit(true);
        cfg.setInitializationFailTimeout(-1); // không chặn khởi động nếu một cụm tạm thời không sẵn sàng
        return new HikariDataSource(cfg);
    }

    public DataSource tenantPool(String clusterId) {
        HikariDataSource ds = tenantPools.get(clusterId);
        if (ds == null) {
            throw new IllegalStateException("Unknown cluster: " + clusterId);
        }
        return ds;
    }

    public boolean hasCluster(String clusterId) {
        return tenantPools.containsKey(clusterId);
    }

    public DataSource controlPlane() {
        return controlPlane;
    }

    public String defaultCluster() {
        return defaultCluster;
    }

    public Map<String, ? extends DataSource> tenantPools() {
        return Map.copyOf(tenantPools);
    }

    @Override
    public void close() {
        tenantPools.values().forEach(HikariDataSource::close);
        controlPlane.close();
    }
}
