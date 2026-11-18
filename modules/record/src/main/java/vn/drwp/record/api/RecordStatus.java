package vn.drwp.record.api;

public enum RecordStatus {
    DRAFT, SUBMITTED, IN_PROGRESS, APPROVED, REJECTED, RETURNED, CANCELLED;

    public boolean editable() {
        return this == DRAFT || this == RETURNED;
    }

    public boolean terminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}
