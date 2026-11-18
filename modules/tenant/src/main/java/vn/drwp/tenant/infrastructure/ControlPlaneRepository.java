package vn.drwp.tenant.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.support.SqlTime;
import vn.drwp.platform.tenancy.TenancyConfiguration;
import vn.drwp.platform.tenancy.TenantPlacement;
import vn.drwp.platform.tenancy.TenantStatus;
import vn.drwp.tenant.api.TenantMembershipReader.MemberView;
import vn.drwp.tenant.application.TenantView;

/** Truy cập control-plane. Mọi câu lệnh dùng tham số bind; tên schema chỉ lấy từ bảng placement. */
@Repository
public class ControlPlaneRepository {

    private final JdbcClient jdbc;
    private final Json json;

    public ControlPlaneRepository(@Qualifier(TenancyConfiguration.CONTROL_PLANE) JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String SELECT_PLACEMENT = """
            SELECT p.tenant_id, p.cluster_id, p.database_id, p.schema_name, p.placement_version, r.status
            FROM tenant_placement p JOIN tenant_registry r ON r.tenant_id = p.tenant_id
            """;

    public Optional<TenantPlacement> placement(TenantId tenantId) {
        return jdbc.sql(SELECT_PLACEMENT + " WHERE p.tenant_id = :id AND p.status = 'ACTIVE'")
                .param("id", tenantId.value())
                .query(ControlPlaneRepository::mapPlacement)
                .optional();
    }

    public Optional<TenantPlacement> placementBySchema(String clusterId, String schema) {
        return jdbc.sql(SELECT_PLACEMENT + " WHERE p.cluster_id = :cluster AND p.schema_name = :schema AND p.status = 'ACTIVE'")
                .param("cluster", clusterId)
                .param("schema", schema)
                .query(ControlPlaneRepository::mapPlacement)
                .optional();
    }

    public List<TenantPlacement> placementsByStatus(TenantStatus status) {
        return jdbc.sql(SELECT_PLACEMENT + " WHERE r.status = :status AND p.status = 'ACTIVE' ORDER BY p.tenant_id")
                .param("status", status.name())
                .query(ControlPlaneRepository::mapPlacement)
                .list();
    }

    private static TenantPlacement mapPlacement(ResultSet rs, int row) throws SQLException {
        return new TenantPlacement(TenantId.of(rs.getString("tenant_id")), rs.getString("cluster_id"),
                rs.getString("database_id"), rs.getString("schema_name"), rs.getLong("placement_version"),
                TenantStatus.valueOf(rs.getString("status")));
    }

    public Optional<TenantView> find(TenantId tenantId) {
        return jdbc.sql("""
                SELECT r.tenant_id, r.display_name, r.status, r.compliance_profiles::text AS profiles, r.last_error,
                       r.created_at, r.updated_at, p.cluster_id, p.schema_name
                FROM tenant_registry r LEFT JOIN tenant_placement p ON p.tenant_id = r.tenant_id
                WHERE r.tenant_id = :id
                """).param("id", tenantId.value()).query(this::mapView).optional();
    }

    public List<TenantView> list() {
        return jdbc.sql("""
                SELECT r.tenant_id, r.display_name, r.status, r.compliance_profiles::text AS profiles, r.last_error,
                       r.created_at, r.updated_at, p.cluster_id, p.schema_name
                FROM tenant_registry r LEFT JOIN tenant_placement p ON p.tenant_id = r.tenant_id
                ORDER BY r.tenant_id
                """).query(this::mapView).list();
    }

    @SuppressWarnings("unchecked")
    private TenantView mapView(ResultSet rs, int row) throws SQLException {
        return new TenantView(rs.getString("tenant_id"), rs.getString("display_name"),
                TenantStatus.valueOf(rs.getString("status")),
                (List<String>) (List<?>) json.readList(rs.getString("profiles")),
                rs.getString("cluster_id"), rs.getString("schema_name"), rs.getString("last_error"),
                SqlTime.instant(rs, "created_at"), SqlTime.instant(rs, "updated_at"));
    }

    public void insertTenant(TenantId tenantId, String displayName, List<String> profiles, String cluster, String schema) {
        jdbc.sql("""
                INSERT INTO tenant_registry (tenant_id, display_name, status, compliance_profiles)
                VALUES (:id, :name, 'PROVISIONING', CAST(:profiles AS jsonb))
                """).param("id", tenantId.value()).param("name", displayName).param("profiles", json.write(profiles)).update();
        jdbc.sql("""
                INSERT INTO tenant_placement (tenant_id, cluster_id, schema_name) VALUES (:id, :cluster, :schema)
                """).param("id", tenantId.value()).param("cluster", cluster).param("schema", schema).update();
    }

    public int updateStatus(TenantId tenantId, TenantStatus expected, TenantStatus target, String lastError) {
        return jdbc.sql("""
                UPDATE tenant_registry SET status = :target, last_error = :err, updated_at = now()
                WHERE tenant_id = :id AND status = :expected
                """).param("target", target.name()).param("err", lastError).param("id", tenantId.value())
                .param("expected", expected.name()).update();
    }

    public void recordError(TenantId tenantId, String error) {
        jdbc.sql("UPDATE tenant_registry SET last_error = :err, updated_at = now() WHERE tenant_id = :id")
                .param("err", error == null ? null : error.substring(0, Math.min(error.length(), 2000)))
                .param("id", tenantId.value()).update();
    }

    // ---- membership ----

    public boolean isActiveMember(TenantId tenantId, String subject) {
        return jdbc.sql("""
                SELECT count(*) FROM tenant_membership m JOIN tenant_registry r ON r.tenant_id = m.tenant_id
                WHERE m.tenant_id = :id AND m.subject = :subject AND m.status = 'ACTIVE' AND r.status = 'ACTIVE'
                """).param("id", tenantId.value()).param("subject", subject).query(Long.class).single() > 0;
    }

    public List<MemberView> members(TenantId tenantId) {
        return jdbc.sql("""
                SELECT subject, display_name, status FROM tenant_membership WHERE tenant_id = :id ORDER BY subject
                """).param("id", tenantId.value())
                .query((rs, n) -> new MemberView(rs.getString("subject"), rs.getString("display_name"), rs.getString("status")))
                .list();
    }

    public List<TenantView> tenantsOfSubject(String subject) {
        return jdbc.sql("""
                SELECT r.tenant_id, r.display_name, r.status, r.compliance_profiles::text AS profiles, NULL AS last_error,
                       r.created_at, r.updated_at, NULL AS cluster_id, NULL AS schema_name
                FROM tenant_membership m JOIN tenant_registry r ON r.tenant_id = m.tenant_id
                WHERE m.subject = :subject AND m.status = 'ACTIVE' AND r.status = 'ACTIVE'
                ORDER BY r.tenant_id
                """).param("subject", subject).query(this::mapView).list();
    }

    public void upsertMember(TenantId tenantId, String subject, String displayName, String by) {
        jdbc.sql("""
                INSERT INTO tenant_membership (tenant_id, subject, display_name, status, created_by)
                VALUES (:id, :subject, :name, 'ACTIVE', :by)
                ON CONFLICT (tenant_id, subject)
                DO UPDATE SET status = 'ACTIVE', display_name = COALESCE(EXCLUDED.display_name, tenant_membership.display_name),
                              updated_at = now()
                """).param("id", tenantId.value()).param("subject", subject).param("name", displayName).param("by", by).update();
    }

    public int suspendMember(TenantId tenantId, String subject) {
        return jdbc.sql("""
                UPDATE tenant_membership SET status = 'SUSPENDED', updated_at = now()
                WHERE tenant_id = :id AND subject = :subject
                """).param("id", tenantId.value()).param("subject", subject).update();
    }

    // ---- audit ----

    public void audit(String actor, String action, TenantId tenantId, Object detail) {
        jdbc.sql("""
                INSERT INTO ctl_audit (actor, action, tenant_id, detail) VALUES (:actor, :action, :tenant, CAST(:detail AS jsonb))
                """).param("actor", actor).param("action", action)
                .param("tenant", tenantId == null ? null : tenantId.value())
                .param("detail", json.write(detail)).update();
    }
}
