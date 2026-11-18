package vn.drwp.tenant.infrastructure;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import vn.drwp.platform.tenancy.TenancyConfiguration;
import vn.drwp.platform.tenancy.TenancyProperties;

/** Migration control-plane (schema {@code platform}) khi khởi động, trước mọi truy vấn control-plane. */
@Component
class ControlPlaneSchemaInitializer implements InitializingBean {

    private final DataSource controlPlane;
    private final TenancyProperties properties;

    ControlPlaneSchemaInitializer(@Qualifier(TenancyConfiguration.CONTROL_PLANE) DataSource controlPlane,
                                  TenancyProperties properties) {
        this.controlPlane = controlPlane;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        Flyway.configure()
                .dataSource(controlPlane)
                .schemas(properties.controlPlane().schema())
                .defaultSchema(properties.controlPlane().schema())
                .createSchemas(true)
                .locations("classpath:db/control")
                .table("flyway_history_control")
                .cleanDisabled(true)
                .load()
                .migrate();
    }
}
