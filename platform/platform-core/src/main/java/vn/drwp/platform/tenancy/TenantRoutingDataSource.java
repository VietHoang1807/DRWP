package vn.drwp.platform.tenancy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.tenant.TenantId;

/**
 * DataSource chính của business-app (ADR 019 + ADR 003).
 * <ol>
 *   <li>Đọc tenant từ {@link TenantContext}; thiếu thì fail-closed.</li>
 *   <li>Tra placement (cụm + schema) từ registry tin cậy.</li>
 *   <li>Lấy connection từ pool của cụm, đặt {@code search_path} = schema tenant.</li>
 *   <li>Connection trả về bị khóa schema: gọi {@code setSchema} từ ngoài bị từ chối; khi đóng reset về schema rỗng.</li>
 * </ol>
 * Vì dữ liệu nghiệp vụ, {@code event_publication} (Spring Modulith) và {@code outbox_event} đều đi qua DataSource này
 * trong cùng transaction, registry và outbox nằm trong đúng schema tenant cùng transaction với dữ liệu.
 */
public class TenantRoutingDataSource extends AbstractDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);

    private final ClusterDataSources clusters;
    private final PlacementCache placements;
    private final TenancyStartupGuard startupGuard;

    public TenantRoutingDataSource(ClusterDataSources clusters, PlacementCache placements, TenancyStartupGuard startupGuard) {
        this.clusters = clusters;
        this.placements = placements;
        this.startupGuard = startupGuard;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Optional<TenantId> tenant = TenantContext.current();
        if (tenant.isEmpty()) {
            if (startupGuard.isStrict()) {
                throw new TenantIsolationException("Connection requested without tenant context");
            }
            log.debug("Handing out no-tenant connection during startup/shutdown");
            Connection raw = clusters.tenantPool(clusters.defaultCluster()).getConnection();
            try {
                // đặt tường minh: Flyway đổi search_path bằng SQL nên không tin trạng thái connection trong pool
                raw.setSchema(SchemaNames.NO_TENANT_SCHEMA);
            } catch (SQLException | RuntimeException e) {
                raw.close();
                throw e;
            }
            return lock(raw, SchemaNames.NO_TENANT_SCHEMA);
        }
        TenantPlacement placement = placements.requireUsable(tenant.get(), TenantContext.isProvisioning());
        Connection raw = clusters.tenantPool(placement.clusterId()).getConnection();
        try {
            raw.setSchema(placement.schemaName());
        } catch (SQLException | RuntimeException e) {
            raw.close();
            throw e;
        }
        return lock(raw, placement.schemaName());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Per-call credentials are not supported by tenant routing");
    }

    private static Connection lock(Connection raw, String schema) {
        InvocationHandler handler = new SchemaLockedHandler(raw, schema);
        return (Connection) Proxy.newProxyInstance(TenantRoutingDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private record SchemaLockedHandler(Connection target, String schema) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setSchema" -> {
                    if (args != null && args.length == 1 && schema.equals(args[0])) {
                        return null;
                    }
                    throw new SQLException("Schema of a tenant-bound connection cannot be changed");
                }
                case "close" -> {
                    try {
                        if (!target.isClosed()) {
                            target.setSchema(SchemaNames.NO_TENANT_SCHEMA);
                        }
                    } finally {
                        target.close();
                    }
                    return null;
                }
                case "unwrap" -> {
                    if (args[0] == Connection.class) {
                        return proxy;
                    }
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "TenantBoundConnection[" + schema + "]";
                }
                default -> {
                }
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
}
