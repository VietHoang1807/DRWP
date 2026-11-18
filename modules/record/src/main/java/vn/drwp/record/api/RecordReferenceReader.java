package vn.drwp.record.api;

import java.util.Optional;
import java.util.UUID;

/** Contract cho Workflow (dependency matrix: Workflow -> Record, Direct). */
public interface RecordReferenceReader {
    Optional<RecordReference> find(UUID recordId);
}
