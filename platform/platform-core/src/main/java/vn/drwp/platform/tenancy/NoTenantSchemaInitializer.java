package vn.drwp.platform.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Tạo schema {@link SchemaNames#NO_TENANT_SCHEMA} trên mỗi cụm: chỉ chứa bảng {@code event_publication} rỗng,
 * mọi lệnh ghi bị trigger chặn. Connection chưa gắn tenant (chỉ trong lúc khởi động/tắt) đọc thấy registry rỗng,
 * không thấy bảng nghiệp vụ nào và không ghi được gì (fail-closed).
 */
public class NoTenantSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(NoTenantSchemaInitializer.class);
    private static final String S = SchemaNames.NO_TENANT_SCHEMA;

    private final ClusterDataSources clusters;

    public NoTenantSchemaInitializer(ClusterDataSources clusters) {
        this.clusters = clusters;
    }

    @Override
    public void afterPropertiesSet() {
        clusters.tenantPools().forEach((id, ds) -> {
            try {
                init(ds);
            } catch (SQLException e) {
                // cụm có thể chưa sẵn sàng; connection no-tenant khi đó vẫn fail-closed (schema không tồn tại)
                log.warn("Could not prepare {} schema on cluster {}: {}", S, id, e.getMessage());
            }
        });
    }

    private static void init(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + S);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s.event_publication (
                      id UUID NOT NULL PRIMARY KEY, listener_id TEXT NOT NULL, event_type TEXT NOT NULL,
                      serialized_event TEXT NOT NULL, publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
                      completion_date TIMESTAMP WITH TIME ZONE, status TEXT, completion_attempts INT,
                      last_resubmission_date TIMESTAMP WITH TIME ZONE)""".formatted(S));
            st.execute("""
                    CREATE OR REPLACE FUNCTION %s.reject_write() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'writes without tenant context are not allowed'; END $$""".formatted(S));
            st.execute("DROP TRIGGER IF EXISTS no_write ON %s.event_publication".formatted(S));
            st.execute("""
                    CREATE TRIGGER no_write BEFORE INSERT OR UPDATE OR DELETE ON %s.event_publication
                    FOR EACH ROW EXECUTE FUNCTION %s.reject_write()""".formatted(S, S));
        }
    }
}
