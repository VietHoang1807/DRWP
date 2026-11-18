package vn.drwp.record.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.support.SqlTime;
import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.domain.DynamicRecord;
import vn.drwp.record.domain.RecordQuery;

@Repository
public class RecordRepository {

    private static final String COLUMNS = """
            id, record_code, form_code, form_version_id, workflow_instance_id, status, dynamic_data::text AS data,
            created_by, created_at, updated_at, submitted_at, completed_at, submission_count, version
            """;

    private final JdbcClient jdbc;
    private final Json json;

    public RecordRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Page(List<DynamicRecord> items, long total) {
    }

    public record Revision(int revisionNo, String changeType, String status, Map<String, Object> data, String changedBy,
                           Instant changedAt) {
    }

    public String nextCode(String formCode, Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        long value = jdbc.sql("""
                INSERT INTO rec_code_sequence (form_code, year, next_value) VALUES (:form, :year, 2)
                ON CONFLICT (form_code, year) DO UPDATE SET next_value = rec_code_sequence.next_value + 1
                RETURNING next_value - 1
                """).param("form", formCode).param("year", year).query(Long.class).single();
        return "%s-%d-%06d".formatted(formCode, year, value);
    }

    public void insert(DynamicRecord r) {
        jdbc.sql("""
                INSERT INTO rec_record (id, record_code, form_code, form_version_id, status, dynamic_data, created_by,
                                        created_at, updated_at, submission_count, version)
                VALUES (:id, :code, :form, :formVersion, :status, CAST(:data AS jsonb), :owner, :createdAt, :createdAt, 0, 0)
                """).param("id", r.id()).param("code", r.recordCode()).param("form", r.formCode())
                .param("formVersion", r.formVersionId()).param("status", r.status().name())
                .param("data", json.write(r.mutableDataCopy())).param("owner", r.owner())
                .param("createdAt", SqlTime.ts(r.createdAt())).update();
    }

    /** Optimistic locking: cập nhật khi version khớp, tăng version. */
    public void update(DynamicRecord r) {
        int n = jdbc.sql("""
                UPDATE rec_record SET status = :status, dynamic_data = CAST(:data AS jsonb),
                       workflow_instance_id = :instance, submitted_at = :submittedAt, completed_at = :completedAt,
                       submission_count = :submissions, updated_at = now(), version = version + 1
                WHERE id = :id AND version = :version
                """).param("status", r.status().name()).param("data", json.write(r.mutableDataCopy()))
                .param("instance", r.workflowInstanceId()).param("submittedAt", SqlTime.ts(r.submittedAt()))
                .param("completedAt", SqlTime.ts(r.completedAt())).param("submissions", r.submissionCount())
                .param("id", r.id()).param("version", r.version()).update();
        if (n != 1) {
            throw new ConflictException("STALE_RECORD", "Record " + r.recordCode() + " was modified concurrently");
        }
    }

    public Optional<DynamicRecord> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM rec_record WHERE id = :id").param("id", id).query(this::map).optional();
    }

    /** Khóa dòng khi cần đọc-sửa trong cùng transaction (ví dụ Workflow cập nhật trạng thái). */
    public Optional<DynamicRecord> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM rec_record WHERE id = :id FOR UPDATE").param("id", id).query(this::map).optional();
    }

    public Page search(RecordQuery q, Set<String> allowedDataKeys, String restrictToOwner) {
        RecordSqlBuilder.Built b = RecordSqlBuilder.build(q, allowedDataKeys, restrictToOwner);
        long total = jdbc.sql("SELECT count(*) FROM rec_record" + b.where()).params(b.params()).query(Long.class).single();
        List<DynamicRecord> items = jdbc.sql("SELECT " + COLUMNS + " FROM rec_record" + b.where() + b.orderBy()
                        + " LIMIT :limit OFFSET :offset")
                .params(b.params()).param("limit", q.size()).param("offset", (long) q.page() * q.size())
                .query(this::map).list();
        return new Page(items, total);
    }

    public void insertRevision(UUID recordId, String changeType, RecordStatus status, Map<String, Object> data, String by) {
        jdbc.sql("""
                INSERT INTO rec_record_revision (id, record_id, revision_no, change_type, status, dynamic_data, changed_by)
                VALUES (:id, :recordId,
                        (SELECT COALESCE(max(revision_no), 0) + 1 FROM rec_record_revision WHERE record_id = :recordId),
                        :changeType, :status, CAST(:data AS jsonb), :by)
                """).param("id", Ids.newId()).param("recordId", recordId).param("changeType", changeType)
                .param("status", status.name()).param("data", json.write(data)).param("by", by).update();
    }

    public List<Revision> revisions(UUID recordId) {
        return jdbc.sql("""
                SELECT revision_no, change_type, status, dynamic_data::text AS data, changed_by, changed_at
                FROM rec_record_revision WHERE record_id = :id ORDER BY revision_no
                """).param("id", recordId)
                .query((rs, n) -> new Revision(rs.getInt("revision_no"), rs.getString("change_type"), rs.getString("status"),
                        json.readMap(rs.getString("data")), rs.getString("changed_by"), SqlTime.instant(rs, "changed_at")))
                .list();
    }

    private DynamicRecord map(ResultSet rs, int n) throws SQLException {
        return new DynamicRecord(rs.getObject("id", UUID.class), rs.getString("record_code"), rs.getString("form_code"),
                rs.getObject("form_version_id", UUID.class), rs.getString("created_by"), SqlTime.instant(rs, "created_at"),
                rs.getLong("version"), rs.getObject("workflow_instance_id", UUID.class),
                RecordStatus.valueOf(rs.getString("status")), json.readMap(rs.getString("data")),
                SqlTime.instant(rs, "submitted_at"), SqlTime.instant(rs, "completed_at"), rs.getInt("submission_count"));
    }
}
