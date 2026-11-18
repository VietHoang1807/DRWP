package vn.drwp.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Hai cụm PostgreSQL cho test cách ly (ADR 024 tầng 2, spike ADR 019): mặc định Testcontainers (2 container);
 * hoặc dùng cụm có sẵn qua biến môi trường DRWP_TEST_DB_A_URL / DRWP_TEST_DB_B_URL (+ _USER/_PASSWORD).
 */
public final class TestDatabases {

    private static String urlA;
    private static String urlB;
    private static String user;
    private static String password;
    private static boolean started;

    private TestDatabases() {
    }

    /** Dùng cho {@code @EnabledIf}: có DB qua env hoặc có Docker. */
    public static boolean available() {
        if (System.getenv("DRWP_TEST_DB_A_URL") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @SuppressWarnings("resource")
    static synchronized void start() {
        if (started) {
            return;
        }
        String envA = System.getenv("DRWP_TEST_DB_A_URL");
        if (envA != null) {
            urlA = envA;
            urlB = System.getenv().getOrDefault("DRWP_TEST_DB_B_URL", envA);
            user = System.getenv().getOrDefault("DRWP_TEST_DB_USER", "drwp");
            password = System.getenv().getOrDefault("DRWP_TEST_DB_PASSWORD", "drwp");
        } else {
            PostgreSQLContainer a = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("drwp").withUsername("drwp").withPassword("drwp");
            PostgreSQLContainer b = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("drwp").withUsername("drwp").withPassword("drwp");
            a.start();
            b.start();
            urlA = a.getJdbcUrl();
            urlB = b.getJdbcUrl();
            user = "drwp";
            password = "drwp";
        }
        started = true;
    }

    static void register(DynamicPropertyRegistry r) {
        start();
        r.add("drwp.tenancy.clusters.cluster-a.url", () -> urlA);
        r.add("drwp.tenancy.clusters.cluster-a.username", () -> user);
        r.add("drwp.tenancy.clusters.cluster-a.password", () -> password);
        r.add("drwp.tenancy.clusters.cluster-b.url", () -> urlB);
        r.add("drwp.tenancy.clusters.cluster-b.username", () -> user);
        r.add("drwp.tenancy.clusters.cluster-b.password", () -> password);
    }

    static String urlB() {
        return urlB;
    }

    static String urlA() {
        return urlA;
    }

    static String user() {
        return user;
    }

    static String password() {
        return password;
    }
}
