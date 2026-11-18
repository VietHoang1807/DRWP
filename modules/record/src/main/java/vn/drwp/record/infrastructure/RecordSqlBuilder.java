package vn.drwp.record.infrastructure;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.drwp.record.domain.RecordQuery;

/**
 * Dựng SQL tìm kiếm từ tiêu chí đã whitelist: tên cột chỉ đến từ enum, giá trị và khóa JSON đều là tham số bind
 * (tài liệu 01 mục 5: "không ghép SQL từ input").
 */
final class RecordSqlBuilder {

    record Built(String where, Map<String, Object> params, String orderBy) {
    }

    private RecordSqlBuilder() {
    }

    static Built build(RecordQuery q, Set<String> allowedDataKeys, String restrictToOwner) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (q.formCode() != null) {
            clauses.add("form_code = :formCode");
            params.put("formCode", q.formCode());
        }
        if (q.status() != null) {
            clauses.add("status = :status");
            params.put("status", q.status().name());
        }
        if (q.createdBy() != null) {
            clauses.add("created_by = :createdBy");
            params.put("createdBy", q.createdBy());
        }
        if (restrictToOwner != null) {
            clauses.add("created_by = :owner");
            params.put("owner", restrictToOwner);
        }
        if (q.recordCode() != null) {
            clauses.add("record_code = :recordCode");
            params.put("recordCode", q.recordCode());
        }
        if (q.createdFrom() != null) {
            clauses.add("created_at >= :createdFrom");
            params.put("createdFrom", Timestamp.from(q.createdFrom()));
        }
        if (q.createdTo() != null) {
            clauses.add("created_at < :createdTo");
            params.put("createdTo", Timestamp.from(q.createdTo()));
        }
        int i = 0;
        for (Map.Entry<String, String> e : q.dataEquals().entrySet()) {
            if (!allowedDataKeys.contains(e.getKey())) {
                throw new IllegalArgumentException("Field is not filterable: " + e.getKey());
            }
            String k = "dk" + i;
            String v = "dv" + i;
            clauses.add("dynamic_data ->> :" + k + " = :" + v);
            params.put(k, e.getKey());
            params.put(v, e.getValue());
            i++;
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        String orderBy = " ORDER BY " + q.sort().column() + (q.descending() ? " DESC" : " ASC") + ", id";
        return new Built(where, params, orderBy);
    }
}
