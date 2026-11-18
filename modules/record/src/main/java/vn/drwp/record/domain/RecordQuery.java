package vn.drwp.record.domain;

import java.time.Instant;
import java.util.Map;

import vn.drwp.record.api.RecordStatus;

/** Tiêu chí tìm kiếm. Trường động chỉ lọc bằng so sánh bằng và phải nằm trong whitelist của biểu mẫu. */
public record RecordQuery(
        String formCode,
        RecordStatus status,
        String createdBy,
        String recordCode,
        Instant createdFrom,
        Instant createdTo,
        Map<String, String> dataEquals,
        Sort sort,
        boolean descending,
        int page,
        int size) {

    public enum Sort {
        CREATED_AT("created_at"), UPDATED_AT("updated_at"), RECORD_CODE("record_code"), STATUS("status");

        private final String column;

        Sort(String column) {
            this.column = column;
        }

        public String column() {
            return column;
        }
    }

    public RecordQuery {
        dataEquals = dataEquals == null ? Map.of() : Map.copyOf(dataEquals);
        sort = sort == null ? Sort.CREATED_AT : sort;
        if (dataEquals.size() > 10) {
            throw new IllegalArgumentException("At most 10 data filters");
        }
    }
}
