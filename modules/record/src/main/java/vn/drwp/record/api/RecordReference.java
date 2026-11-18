package vn.drwp.record.api;

import java.util.Map;
import java.util.UUID;

/** Ảnh chụp hồ sơ cho module khác (không phải entity). */
public record RecordReference(UUID id, String recordCode, String formCode, UUID formVersionId, RecordStatus status,
                              String owner, Map<String, Object> data, long version) {
}
