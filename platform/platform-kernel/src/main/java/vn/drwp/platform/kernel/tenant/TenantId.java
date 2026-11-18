package vn.drwp.platform.kernel.tenant;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Định danh tenant. Chỉ chấp nhận chữ thường, số và dấu gạch ngang để có thể suy ra
 * tên schema an toàn (ADR 003, tài liệu 02 mục 3 "SQL schema injection").
 */
public record TenantId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]{1,39}$");

    public TenantId {
        Objects.requireNonNull(value, "tenantId");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid tenant id: must match " + FORMAT.pattern());
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    /** Tên schema mặc định cho tenant: {@code tenant_<id>} với '-' thay bằng '_'. */
    public String defaultSchemaName() {
        return "tenant_" + value.replace('-', '_');
    }

    @Override
    public String toString() {
        return value;
    }
}
