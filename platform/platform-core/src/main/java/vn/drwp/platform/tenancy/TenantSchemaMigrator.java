package vn.drwp.platform.tenancy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chạy migration cho một schema tenant, lần lượt theo từng module (thứ tự {@link TenantSchemaModule#order()}).
 * Flyway dùng pool của cụm chứa tenant (không qua routing) và luôn chỉ định schema tường minh.
 */
public class TenantSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrator.class);

    private final ClusterDataSources clusters;
    private final List<TenantSchemaModule> modules;

    public TenantSchemaMigrator(ClusterDataSources clusters, List<TenantSchemaModule> modules) {
        this.clusters = clusters;
        this.modules = modules.stream().sorted(Comparator.comparingInt(TenantSchemaModule::order)).toList();
    }

    public void migrate(TenantPlacement placement) {
        String schema = SchemaNames.requireValid(placement.schemaName());
        for (TenantSchemaModule module : modules) {
            var result = Flyway.configure()
                    .dataSource(clusters.tenantPool(placement.clusterId()))
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations(module.location())
                    .table(module.historyTable())
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .placeholders(Map.of("tenant_schema", schema, "tenant_id", placement.tenantId().value()))
                    .cleanDisabled(true)
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
            if (result.migrationsExecuted > 0) {
                log.info("Tenant {} module {}: applied {} migration(s)", placement.tenantId(), module.id(), result.migrationsExecuted);
            }
        }
    }

    public List<TenantSchemaModule> modules() {
        return modules;
    }
}
