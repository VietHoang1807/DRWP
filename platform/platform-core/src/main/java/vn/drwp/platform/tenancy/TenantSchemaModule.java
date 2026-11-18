package vn.drwp.platform.tenancy;

import java.util.regex.Pattern;

/**
 * Mỗi module khai báo một bean này để migrator chạy Flyway riêng cho module:
 * location {@code classpath:db/tenant/<id>}, bảng lịch sử {@code flyway_history_<id>}.
 * Mỗi module tự đánh version migration của mình (ADR 015: module sở hữu bảng).
 */
public record TenantSchemaModule(String id, int order) {

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]{1,20}$");

    public TenantSchemaModule {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Illegal schema module id: " + id);
        }
    }

    public String location() {
        return "classpath:db/tenant/" + id;
    }

    public String historyTable() {
        return "flyway_history_" + id;
    }
}
