package vn.drwp.record.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.record.api.RecordOutcome;
import vn.drwp.record.api.RecordStatus;

/** Aggregate hồ sơ động. Luật chuyển trạng thái nằm ở đây, không phụ thuộc framework. */
public final class DynamicRecord {

    private final UUID id;
    private final String recordCode;
    private final String formCode;
    private final UUID formVersionId;
    private final String owner;
    private final Instant createdAt;
    private final long version;
    private UUID workflowInstanceId;
    private RecordStatus status;
    private Map<String, Object> data;
    private Instant submittedAt;
    private Instant completedAt;
    private int submissionCount;

    public DynamicRecord(UUID id, String recordCode, String formCode, UUID formVersionId, String owner, Instant createdAt,
                         long version, UUID workflowInstanceId, RecordStatus status, Map<String, Object> data,
                         Instant submittedAt, Instant completedAt, int submissionCount) {
        this.id = Objects.requireNonNull(id);
        this.recordCode = recordCode;
        this.formCode = formCode;
        this.formVersionId = formVersionId;
        this.owner = owner;
        this.createdAt = createdAt;
        this.version = version;
        this.workflowInstanceId = workflowInstanceId;
        this.status = status;
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
        this.submissionCount = submissionCount;
    }

    public static DynamicRecord draft(UUID id, String recordCode, String formCode, UUID formVersionId, String owner,
                                      Map<String, Object> data, Instant now) {
        return new DynamicRecord(id, recordCode, formCode, formVersionId, owner, now, 0, null, RecordStatus.DRAFT, data,
                null, null, 0);
    }

    public void updateData(String actor, Map<String, Object> newData) {
        requireOwner(actor);
        if (!status.editable()) {
            throw new InvalidStateException("Record " + recordCode + " cannot be edited in status " + status);
        }
        this.data = newData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(newData);
    }

    public void submit(String actor, Instant now) {
        requireOwner(actor);
        if (!status.editable()) {
            throw new InvalidStateException("Record " + recordCode + " cannot be submitted in status " + status);
        }
        status = RecordStatus.SUBMITTED;
        submittedAt = now;
        workflowInstanceId = null;
        submissionCount++;
    }

    public void markInProgress(UUID instanceId) {
        if (status != RecordStatus.SUBMITTED) {
            throw new InvalidStateException("Record " + recordCode + " is not awaiting a workflow (status " + status + ")");
        }
        status = RecordStatus.IN_PROGRESS;
        workflowInstanceId = instanceId;
    }

    public void complete(RecordOutcome outcome, Instant now) {
        if (status != RecordStatus.IN_PROGRESS && status != RecordStatus.SUBMITTED) {
            throw new InvalidStateException("Record " + recordCode + " has no running workflow (status " + status + ")");
        }
        status = outcome.toStatus();
        if (status.terminal()) {
            completedAt = now;
        }
    }

    public void cancel(String actor, Instant now) {
        requireOwner(actor);
        if (!status.editable()) {
            throw new InvalidStateException("Only DRAFT/RETURNED records can be cancelled by the owner");
        }
        status = RecordStatus.CANCELLED;
        completedAt = now;
    }

    public boolean isOwner(String subject) {
        return owner.equals(subject);
    }

    private void requireOwner(String actor) {
        if (!isOwner(actor)) {
            throw new ForbiddenException("Only the owner can change this record");
        }
    }

    public UUID id() { return id; }
    public String recordCode() { return recordCode; }
    public String formCode() { return formCode; }
    public UUID formVersionId() { return formVersionId; }
    public String owner() { return owner; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
    public UUID workflowInstanceId() { return workflowInstanceId; }
    public RecordStatus status() { return status; }
    public Map<String, Object> data() { return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(data)); }
    public Map<String, Object> mutableDataCopy() { return new LinkedHashMap<>(data); }
    public Instant submittedAt() { return submittedAt; }
    public Instant completedAt() { return completedAt; }
    public int submissionCount() { return submissionCount; }
}
