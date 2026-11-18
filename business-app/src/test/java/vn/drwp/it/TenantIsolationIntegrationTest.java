package vn.drwp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.ClusterDataSources;
import vn.drwp.platform.tenancy.TenantContext;

/**
 * Spike bắt buộc đầu Phase 1 (tài liệu 05) + ADR 024 tầng 2:
 * registry đúng schema cùng transaction, listener nhận đúng tenant, sự kiện lệch tenant bị từ chối,
 * hai tenant ở hai cụm PostgreSQL khác nhau, pool không rò schema.
 */
@EnabledIf(value = "vn.drwp.it.TestDatabases#available", disabledReason = "Cần Docker hoặc DRWP_TEST_DB_A_URL")
class TenantIsolationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    DataSource dataSource;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    ClusterDataSources clusters;

    String tenantA;
    String tenantB;
    String adminA;
    String adminB;

    @BeforeEach
    void provisionTwoTenantsOnTwoClusters() throws Exception {
        tenantA = uniqueTenant("iso-a");
        tenantB = uniqueTenant("iso-b");
        adminA = provision(tenantA, "admin-" + tenantA, "cluster-a");
        adminB = provision(tenantB, "admin-" + tenantB, "cluster-b");
    }

    static Map<String, Object> simpleForm() {
        return Map.of("fields", List.of(Map.of("key", "title", "label", "Tiêu đề", "type", "TEXT", "required", true)));
    }

    @Test
    void apiDataIsIsolatedPerTenant() throws Exception {
        Response created = call(HttpMethod.POST, "/api/metadata/forms", adminA, tenantA,
                Map.of("code", "SECRET_FORM", "name", "Chỉ tenant A", "schema", simpleForm()));
        assertThat(created.status()).isEqualTo(201);

        Response listB = call(HttpMethod.GET, "/api/metadata/forms", adminB, tenantB, null);
        assertThat(listB.status()).isEqualTo(200);
        assertThat(listB.list()).isEmpty();

        // admin A chọn tenant B: không có membership => 403 (không tin header client)
        Response crossTenant = call(HttpMethod.GET, "/api/metadata/forms", adminA, tenantB, null);
        assertThat(crossTenant.status()).isEqualTo(403);
        assertThat(crossTenant.str("code")).isEqualTo("TENANT_ACCESS_DENIED");

        Response noTenant = call(HttpMethod.GET, "/api/metadata/forms", adminA, null, null);
        assertThat(noTenant.status()).isEqualTo(400);

        Response unauthenticated = call(HttpMethod.GET, "/api/metadata/forms", null, tenantA, null);
        assertThat(unauthenticated.status()).isEqualTo(401);

        Response notPlatformAdmin = call(HttpMethod.GET, "/api/platform/tenants", adminA, null, null);
        assertThat(notPlatformAdmin.status()).isEqualTo(403);
    }

    @Test
    void tenantsArePlacedOnTheirOwnCluster() throws Exception {
        String schemaB = TenantId.of(tenantB).defaultSchemaName();
        try (Connection b = DriverManager.getConnection(TestDatabases.urlB(), TestDatabases.user(), TestDatabases.password());
             Connection a = DriverManager.getConnection(TestDatabases.urlA(), TestDatabases.user(), TestDatabases.password())) {
            assertThat(schemaExists(b, schemaB)).isTrue();
            if (!TestDatabases.urlA().equals(TestDatabases.urlB())) {
                assertThat(schemaExists(a, schemaB)).isFalse();
            }
        }
        String current = TenantContext.callAs(TenantId.of(tenantB),
                () -> jdbc.sql("SELECT current_schema()").query(String.class).single());
        assertThat(current).isEqualTo(schemaB);
    }

    private static boolean schemaExists(Connection c, String schema) throws Exception {
        try (var ps = c.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Test
    void connectionWithoutTenantContextFailsClosed() {
        assertThatThrownBy(() -> dataSource.getConnection()).isInstanceOf(TenantIsolationException.class);
    }

    @Test
    void pooledConnectionsNeverLeakSchema() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String tenant = i % 2 == 0 ? tenantA : tenantB;
                results.add(pool.submit(() -> TenantContext.callAs(TenantId.of(tenant), () -> {
                    String schema = jdbc.sql("SELECT current_schema()").query(String.class).single();
                    return TenantId.of(tenant).defaultSchemaName().equals(schema);
                })));
            }
            for (Future<Boolean> f : results) {
                assertThat(f.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        // connection trả về pool đã bị reset về schema no-tenant: không thấy bảng nghiệp vụ nào
        try (Connection raw = clusters.tenantPool("cluster-a").getConnection();
             var rs = raw.createStatement().executeQuery("SELECT current_schema(), to_regclass('rec_record') IS NULL")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo(vn.drwp.platform.tenancy.SchemaNames.NO_TENANT_SCHEMA);
            assertThat(rs.getBoolean(2)).isTrue();
        }
    }

    @Test
    void schemaOfTenantBoundConnectionCannotBeChanged() {
        assertThatThrownBy(() -> TenantContext.runAs(TenantId.of(tenantA), () -> {
            try (Connection c = dataSource.getConnection()) {
                c.setSchema(TenantId.of(tenantB).defaultSchemaName());
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        })).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void registryIsWrittenInTenantSchemaAndListenerRunsInSameTenant() throws Exception {
        call(HttpMethod.POST, "/api/metadata/forms", adminA, tenantA,
                Map.of("code", "AUDITED_FORM", "name", "Audit", "schema", simpleForm()));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Long auditA = TenantContext.callAs(TenantId.of(tenantA), () -> jdbc
                    .sql("SELECT count(*) FROM aud_event WHERE action = 'FORM_CREATED'").query(Long.class).single());
            assertThat(auditA).isEqualTo(1L);
        });
        Long publicationsA = TenantContext.callAs(TenantId.of(tenantA), () -> jdbc
                .sql("SELECT count(*) FROM event_publication WHERE event_type LIKE '%BusinessAuditEvent'").query(Long.class).single());
        Long auditB = TenantContext.callAs(TenantId.of(tenantB), () -> jdbc
                .sql("SELECT count(*) FROM aud_event WHERE action = 'FORM_CREATED'").query(Long.class).single());
        assertThat(publicationsA).isGreaterThanOrEqualTo(1L);
        assertThat(auditB).isZero();
    }

    @Test
    void eventWithForeignTenantIdIsRejectedByListener() {
        String marker = "FORGED_" + System.nanoTime();
        // Sự kiện ghi trong transaction của tenant A nhưng khai tenantId = B (giả mạo)
        TenantContext.runAs(TenantId.of(tenantA), () -> tx.executeWithoutResult(s -> events.publishEvent(
                new BusinessAuditEvent(tenantB, "mallory", marker, "x", "1", BusinessAuditEvent.Outcome.SUCCESS,
                        Map.of(), null, null))));
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Long incomplete = TenantContext.callAs(TenantId.of(tenantA), () -> jdbc.sql("""
                    SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE :m
                    """).param("m", "%" + marker + "%").query(Long.class).single());
            assertThat(incomplete).isEqualTo(1L);
        });
        for (String t : List.of(tenantA, tenantB)) {
            Long written = TenantContext.callAs(TenantId.of(t), () -> jdbc
                    .sql("SELECT count(*) FROM aud_event WHERE action = :m").param("m", marker).query(Long.class).single());
            assertThat(written).isZero();
        }
    }

    @Test
    void replayJobResubmitsIncompletePublicationsPerTenant() throws Exception {
        for (String[] t : new String[][]{{tenantA, adminA}, {tenantB, adminB}}) {
            assertThat(call(HttpMethod.POST, "/api/metadata/forms", t[1], t[0],
                    Map.of("code", "REPLAY_FORM", "name", "Replay", "schema", simpleForm())).status()).isEqualTo(201);
        }
        String countSql = "SELECT count(*) FROM aud_event WHERE action = 'FORM_CREATED'";
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            for (String t : List.of(tenantA, tenantB)) {
                assertThat(TenantContext.callAs(TenantId.of(t), () -> jdbc.sql(countSql).query(Long.class).single())).isEqualTo(1L);
            }
        });
        // giả lập listener đã lỗi một giờ trước: publication chưa hoàn tất ở CẢ hai tenant/hai cụm
        String reopen = """
                UPDATE event_publication SET completion_date = NULL, status = 'FAILED', publication_date = now() - interval '1 hour'
                WHERE event_type LIKE '%BusinessAuditEvent' AND serialized_event LIKE '%FORM_CREATED%'
                """;
        for (String t : List.of(tenantA, tenantB)) {
            Integer reopened = TenantContext.callAs(TenantId.of(t), () -> tx.execute(s -> jdbc.sql(reopen).update()));
            assertThat(reopened).isEqualTo(1);
        }
        Object job = context.getBean("eventPublicationMaintenanceJob");
        String incompleteSql = "SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE '%FORM_CREATED%'";
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            // gọi trong vòng chờ: nếu lịch chạy tự động đang giữ ShedLock thì lần sau sẽ chạy được
            job.getClass().getMethod("replayIncompletePublications").invoke(job);
            assertThat(TenantContext.current()).isEmpty();
            for (String t : List.of(tenantA, tenantB)) {
                assertThat(TenantContext.callAs(TenantId.of(t), () -> jdbc.sql(incompleteSql).query(Long.class).single())).isZero();
                // listener audit idempotent: phát lại không ghi trùng
                assertThat(TenantContext.callAs(TenantId.of(t), () -> jdbc.sql(countSql).query(Long.class).single())).isEqualTo(1L);
            }
        });
    }
}
