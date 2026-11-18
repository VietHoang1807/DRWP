package vn.drwp.workflow.api;

import java.util.UUID;

/** Contract cho File: người tham gia quy trình của hồ sơ được xem tệp đính kèm. */
public interface WorkflowParticipation {
    boolean isParticipant(UUID recordId, String subject);
}
