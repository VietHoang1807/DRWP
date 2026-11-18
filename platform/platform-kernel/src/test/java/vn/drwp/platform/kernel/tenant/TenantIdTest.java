package vn.drwp.platform.kernel.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void derivesSafeSchemaName() {
        assertEquals("tenant_so_y_te", TenantId.of("so-y-te").defaultSchemaName());
    }

    @Test
    void rejectsInjectionAttempts() {
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("a\"; DROP SCHEMA x; --"));
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("Tenant"));
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("1abc"));
        assertThrows(IllegalArgumentException.class, () -> TenantId.of("a"));
    }
}
