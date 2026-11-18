package vn.drwp.record.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.record.api.RecordOutcome;
import vn.drwp.record.api.RecordStatus;

class DynamicRecordTest {

    private DynamicRecord draft() {
        return DynamicRecord.draft(UUID.randomUUID(), "LEAVE-2026-000001", "LEAVE", UUID.randomUUID(), "alice",
                Map.of("days", 2), Instant.now());
    }

    @Test
    void lifecycleThroughWorkflow() {
        DynamicRecord r = draft();
        r.submit("alice", Instant.now());
        assertEquals(RecordStatus.SUBMITTED, r.status());
        assertEquals(1, r.submissionCount());
        r.markInProgress(UUID.randomUUID());
        r.complete(RecordOutcome.RETURNED, Instant.now());
        assertEquals(RecordStatus.RETURNED, r.status());
        r.updateData("alice", Map.of("days", 1));
        r.submit("alice", Instant.now());
        assertEquals(2, r.submissionCount());
        r.markInProgress(UUID.randomUUID());
        r.complete(RecordOutcome.APPROVED, Instant.now());
        assertEquals(RecordStatus.APPROVED, r.status());
        assertThrows(InvalidStateException.class, () -> r.updateData("alice", Map.of()));
    }

    @Test
    void onlyOwnerChangesData() {
        DynamicRecord r = draft();
        assertThrows(ForbiddenException.class, () -> r.updateData("mallory", Map.of()));
        assertThrows(ForbiddenException.class, () -> r.submit("mallory", Instant.now()));
    }

    @Test
    void cannotStartWorkflowTwice() {
        DynamicRecord r = draft();
        r.submit("alice", Instant.now());
        r.markInProgress(UUID.randomUUID());
        assertThrows(InvalidStateException.class, () -> r.markInProgress(UUID.randomUUID()));
    }

    @Test
    void dataMayContainNulls() {
        java.util.HashMap<String, Object> data = new java.util.HashMap<>();
        data.put("note", null);
        DynamicRecord r = DynamicRecord.draft(UUID.randomUUID(), "X-1", "X", UUID.randomUUID(), "a", data, Instant.now());
        assertEquals(1, r.data().size());
    }
}
