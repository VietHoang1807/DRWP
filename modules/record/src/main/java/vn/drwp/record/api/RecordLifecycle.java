package vn.drwp.record.api;

import java.util.UUID;

/**
 * Contract để Workflow (process manager, ADR 017) cập nhật trạng thái hồ sơ trong CÙNG transaction với
 * bước chuyển trạng thái quy trình. Xem ADR 025 (đề xuất) trong docs/adr.
 */
public interface RecordLifecycle {

    void markInProgress(UUID recordId, UUID workflowInstanceId);

    void complete(UUID recordId, RecordOutcome outcome, String actor);
}
