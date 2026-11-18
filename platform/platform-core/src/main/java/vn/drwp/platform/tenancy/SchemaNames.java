package vn.drwp.platform.tenancy;

import java.util.regex.Pattern;

/** Allowlist định danh schema; tên schema chỉ đến từ placement registry tin cậy. */
public final class SchemaNames {

    private static final Pattern TENANT_SCHEMA = Pattern.compile("^tenant_[a-z0-9_]{1,50}$");

    /** Schema không tồn tại: connection chưa gắn tenant không thấy bảng nào (fail-closed). */
    public static final String NO_TENANT_SCHEMA = "drwp_no_tenant";

    private SchemaNames() {
    }

    public static boolean isValid(String schema) {
        return schema != null && TENANT_SCHEMA.matcher(schema).matches();
    }

    public static String requireValid(String schema) {
        if (!isValid(schema)) {
            throw new IllegalArgumentException("Illegal tenant schema name: " + schema);
        }
        return schema;
    }
}
