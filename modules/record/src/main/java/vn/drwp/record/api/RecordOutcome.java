package vn.drwp.record.api;

/** Kết quả quy trình áp lên hồ sơ. RETURNED: trả lại người nộp để bổ sung và nộp lại. */
public enum RecordOutcome {
    APPROVED, REJECTED, RETURNED, CANCELLED;

    public RecordStatus toStatus() {
        return RecordStatus.valueOf(name());
    }
}
