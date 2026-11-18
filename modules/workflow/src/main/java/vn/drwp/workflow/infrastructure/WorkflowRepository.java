package vn.drwp.workflow.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.support.SqlTime;

@Repository
public class WorkflowRepository {

    private final JdbcClient jdbc;
    private final Json json;

    public WorkflowRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ------------------------------------------------------------------ definitions

    public record DefinitionRow(UUID id, String code, String name, String description, String formCode, String status,
                                String createdBy, Instant createdAt) {
    }

    public record VersionRow(UUID id, UUID definitionId, String definitionCode, int versionNo, String status,
                             Map<String, Object> spec, String checksum, String createdBy, Instant createdAt,
                             String publishedBy, Instant publishedAt) {
    }

    private static final String DEF_COLS = "id, code, name, description, form_code, status, created_by, created_at";
    private static final String VER_SELECT = """
            SELECT v.id, v.definition_id, d.code, v.version_no, v.status, v.spec::text AS spec, v.checksum, v.created_by,
                   v.created_at, v.published_by, v.published_at
            FROM wf_version v JOIN wf_definition d ON d.id = v.definition_id
            """;

    public Optional<DefinitionRow> definition(String code) {
        return jdbc.sql("SELECT " + DEF_COLS + " FROM wf_definition WHERE code = :code").param("code", code)
                .query(WorkflowRepository::mapDef).optional();
    }

    public Optional<DefinitionRow> activeDefinitionForForm(String formCode) {
        return jdbc.sql("SELECT " + DEF_COLS + " FROM wf_definition WHERE form_code = :form AND status = 'ACTIVE'")
                .param("form", formCode).query(WorkflowRepository::mapDef).optional();
    }

    public List<DefinitionRow> definitions() {
        return jdbc.sql("SELECT " + DEF_COLS + " FROM wf_definition ORDER BY code").query(WorkflowRepository::mapDef).list();
    }

    private static DefinitionRow mapDef(ResultSet rs, int n) throws SQLException {
        return new DefinitionRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("form_code"), rs.getString("status"), rs.getString("created_by"),
                SqlTime.instant(rs, "created_at"));
    }

    public void insertDefinition(UUID id, String code, String name, String description, String formCode, String by) {
        jdbc.sql("""
                INSERT INTO wf_definition (id, code, name, description, form_code, created_by)
                VALUES (:id, :code, :name, :description, :form, :by)
                """).param("id", id).param("code", code).param("name", name).param("description", description)
                .param("form", formCode).param("by", by).update();
    }

    public int updateDefinition(String code, String name, String description, String status) {
        return jdbc.sql("""
                UPDATE wf_definition SET name = :name, description = :description, status = :status, updated_at = now()
                WHERE code = :code
                """).param("name", name).param("description", description).param("status", status).param("code", code).update();
    }

    public void insertVersion(UUID id, UUID definitionId, int versionNo, Map<String, Object> spec, String by) {
        jdbc.sql("""
                INSERT INTO wf_version (id, definition_id, version_no, status, spec, created_by)
                VALUES (:id, :def, :no, 'DRAFT', CAST(:spec AS jsonb), :by)
                """).param("id", id).param("def", definitionId).param("no", versionNo).param("spec", json.write(spec))
                .param("by", by).update();
    }

    public int updateDraft(UUID versionId, Map<String, Object> spec) {
        return jdbc.sql("UPDATE wf_version SET spec = CAST(:spec AS jsonb), updated_at = now() WHERE id = :id AND status = 'DRAFT'")
                .param("spec", json.write(spec)).param("id", versionId).update();
    }

    public int publish(UUID versionId, String checksum, String by) {
        return jdbc.sql("""
                UPDATE wf_version SET status = 'PUBLISHED', checksum = :checksum, published_by = :by, published_at = now(),
                       updated_at = now()
                WHERE id = :id AND status = 'DRAFT'
                """).param("checksum", checksum).param("by", by).param("id", versionId).update();
    }

    public int deprecate(UUID versionId) {
        return jdbc.sql("UPDATE wf_version SET status = 'DEPRECATED', updated_at = now() WHERE id = :id AND status = 'PUBLISHED'")
                .param("id", versionId).update();
    }

    public Optional<VersionRow> version(UUID id) {
        return jdbc.sql(VER_SELECT + " WHERE v.id = :id").param("id", id).query(this::mapVer).optional();
    }

    public Optional<VersionRow> version(String code, int versionNo) {
        return jdbc.sql(VER_SELECT + " WHERE d.code = :code AND v.version_no = :no").param("code", code)
                .param("no", versionNo).query(this::mapVer).optional();
    }

    public List<VersionRow> versions(String code) {
        return jdbc.sql(VER_SELECT + " WHERE d.code = :code ORDER BY v.version_no DESC").param("code", code)
                .query(this::mapVer).list();
    }

    public Optional<VersionRow> latestPublished(UUID definitionId) {
        return jdbc.sql(VER_SELECT + " WHERE v.definition_id = :id AND v.status = 'PUBLISHED' ORDER BY v.version_no DESC LIMIT 1")
                .param("id", definitionId).query(this::mapVer).optional();
    }

    public int maxVersionNo(UUID definitionId) {
        return jdbc.sql("SELECT COALESCE(max(version_no), 0) FROM wf_version WHERE definition_id = :id")
                .param("id", definitionId).query(Integer.class).single();
    }

    private VersionRow mapVer(ResultSet rs, int n) throws SQLException {
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("definition_id", UUID.class), rs.getString("code"),
                rs.getInt("version_no"), rs.getString("status"), json.readMap(rs.getString("spec")), rs.getString("checksum"),
                rs.getString("created_by"), SqlTime.instant(rs, "created_at"), rs.getString("published_by"),
                SqlTime.instant(rs, "published_at"));
    }

    // ------------------------------------------------------------------ runtime

    public record InstanceRow(UUID id, UUID definitionId, String definitionCode, UUID versionId, int versionNo, UUID recordId,
                              String recordCode, String recordOwner, String status, String currentState, String outcome,
                              String startedBy, Instant startedAt, Instant endedAt, long version) {
    }

    public record TaskRow(UUID id, UUID instanceId, String stateCode, String name, String assigneeType, String assigneeValue,
                          String status, Instant dueAt, String completedBy, String completedAction, String comment,
                          Instant createdAt, Instant completedAt, long version) {
    }

    public record HistoryRow(int seq, UUID taskId, String fromState, String toState, String transitionCode,
                             boolean automatic, String actor, String comment, Instant occurredAt) {
    }

    private static final String INST_COLS = """
            id, definition_id, definition_code, version_id, version_no, record_id, record_code, record_owner, status,
            current_state, outcome, started_by, started_at, ended_at, version
            """;
    private static final String TASK_COLS = """
            t.id, t.instance_id, t.state_code, t.name, t.assignee_type, t.assignee_value, t.status, t.due_at,
            t.completed_by, t.completed_action, t.comment, t.created_at, t.completed_at, t.version
            """;

    public boolean instanceExistsForTrigger(UUID triggerId) {
        return jdbc.sql("SELECT count(*) FROM wf_instance WHERE trigger_id = :t").param("t", triggerId)
                .query(Long.class).single() > 0;
    }

    public void insertInstance(InstanceRow i, UUID triggerId) {
        jdbc.sql("""
                INSERT INTO wf_instance (id, definition_id, definition_code, version_id, version_no, record_id, record_code,
                                         record_owner, trigger_id, status, current_state, outcome, started_by, started_at, ended_at)
                VALUES (:id, :def, :defCode, :ver, :verNo, :record, :recordCode, :owner, :trigger, :status, :state, :outcome,
                        :startedBy, :startedAt, :endedAt)
                """).param("id", i.id()).param("def", i.definitionId()).param("defCode", i.definitionCode())
                .param("ver", i.versionId()).param("verNo", i.versionNo()).param("record", i.recordId())
                .param("recordCode", i.recordCode()).param("owner", i.recordOwner()).param("trigger", triggerId)
                .param("status", i.status()).param("state", i.currentState()).param("outcome", i.outcome())
                .param("startedBy", i.startedBy()).param("startedAt", SqlTime.ts(i.startedAt()))
                .param("endedAt", SqlTime.ts(i.endedAt())).update();
    }

    public void updateInstance(UUID id, long expectedVersion, String status, String currentState, String outcome, Instant endedAt) {
        int n = jdbc.sql("""
                UPDATE wf_instance SET status = :status, current_state = :state, outcome = :outcome, ended_at = :endedAt,
                       version = version + 1
                WHERE id = :id AND version = :version
                """).param("status", status).param("state", currentState).param("outcome", outcome)
                .param("endedAt", SqlTime.ts(endedAt)).param("id", id).param("version", expectedVersion).update();
        if (n != 1) {
            throw new ConflictException("STALE_WORKFLOW", "Workflow instance " + id + " was modified concurrently");
        }
    }

    public Optional<InstanceRow> instance(UUID id, boolean forUpdate) {
        return jdbc.sql("SELECT " + INST_COLS + " FROM wf_instance WHERE id = :id" + (forUpdate ? " FOR UPDATE" : ""))
                .param("id", id).query(WorkflowRepository::mapInstance).optional();
    }

    public List<InstanceRow> instancesOfRecord(UUID recordId) {
        return jdbc.sql("SELECT " + INST_COLS + " FROM wf_instance WHERE record_id = :r ORDER BY started_at DESC")
                .param("r", recordId).query(WorkflowRepository::mapInstance).list();
    }

    private static InstanceRow mapInstance(ResultSet rs, int n) throws SQLException {
        return new InstanceRow(rs.getObject("id", UUID.class), rs.getObject("definition_id", UUID.class),
                rs.getString("definition_code"), rs.getObject("version_id", UUID.class), rs.getInt("version_no"),
                rs.getObject("record_id", UUID.class), rs.getString("record_code"), rs.getString("record_owner"),
                rs.getString("status"), rs.getString("current_state"), rs.getString("outcome"), rs.getString("started_by"),
                SqlTime.instant(rs, "started_at"), SqlTime.instant(rs, "ended_at"), rs.getLong("version"));
    }

    public void insertTask(UUID id, UUID instanceId, String stateCode, String name, String assigneeType, String assigneeValue,
                           Instant dueAt) {
        jdbc.sql("""
                INSERT INTO wf_task (id, instance_id, state_code, name, assignee_type, assignee_value, status, due_at)
                VALUES (:id, :instance, :state, :name, :atype, :avalue, 'OPEN', :due)
                """).param("id", id).param("instance", instanceId).param("state", stateCode).param("name", name)
                .param("atype", assigneeType).param("avalue", assigneeValue).param("due", SqlTime.ts(dueAt)).update();
    }

    public void completeTask(UUID taskId, long expectedVersion, String status, String by, String action, String comment) {
        int n = jdbc.sql("""
                UPDATE wf_task SET status = :status, completed_by = :by, completed_action = :action, comment = :comment,
                       completed_at = now(), version = version + 1
                WHERE id = :id AND status = 'OPEN' AND version = :version
                """).param("status", status).param("by", by).param("action", action).param("comment", comment)
                .param("id", taskId).param("version", expectedVersion).update();
        if (n != 1) {
            throw new ConflictException("TASK_ALREADY_HANDLED", "Task " + taskId + " was already handled");
        }
    }

    public int cancelOpenTasks(UUID instanceId, String by) {
        return jdbc.sql("""
                UPDATE wf_task SET status = 'CANCELLED', completed_by = :by, completed_at = now(), version = version + 1
                WHERE instance_id = :id AND status = 'OPEN'
                """).param("by", by).param("id", instanceId).update();
    }

    public Optional<TaskRow> task(UUID id, boolean forUpdate) {
        return jdbc.sql("SELECT " + TASK_COLS + " FROM wf_task t WHERE t.id = :id" + (forUpdate ? " FOR UPDATE" : ""))
                .param("id", id).query(WorkflowRepository::mapTask).optional();
    }

    public List<TaskRow> tasksOfInstance(UUID instanceId) {
        return jdbc.sql("SELECT " + TASK_COLS + " FROM wf_task t WHERE t.instance_id = :id ORDER BY t.created_at")
                .param("id", instanceId).query(WorkflowRepository::mapTask).list();
    }

    /** Hộp việc: task OPEN giao trực tiếp cho subject hoặc cho một trong các vai trò của subject. */
    public List<TaskRow> inbox(String subject, Collection<String> roles, int limit, long offset) {
        return jdbc.sql("SELECT " + TASK_COLS + " FROM wf_task t WHERE t.status = 'OPEN' AND ("
                        + "(t.assignee_type = 'USER' AND t.assignee_value = :subject)"
                        + (roles.isEmpty() ? "" : " OR (t.assignee_type = 'ROLE' AND t.assignee_value IN (:roles))")
                        + ") ORDER BY t.created_at LIMIT :limit OFFSET :offset")
                .param("subject", subject).params(roles.isEmpty() ? Map.of() : Map.of("roles", roles))
                .param("limit", limit).param("offset", offset)
                .query(WorkflowRepository::mapTask).list();
    }

    public long inboxCount(String subject, Collection<String> roles) {
        return jdbc.sql("SELECT count(*) FROM wf_task t WHERE t.status = 'OPEN' AND ("
                        + "(t.assignee_type = 'USER' AND t.assignee_value = :subject)"
                        + (roles.isEmpty() ? "" : " OR (t.assignee_type = 'ROLE' AND t.assignee_value IN (:roles))") + ")")
                .param("subject", subject).params(roles.isEmpty() ? Map.of() : Map.of("roles", roles))
                .query(Long.class).single();
    }

    public List<TaskRow> overdueTasks(Instant now, int limit) {
        return jdbc.sql("SELECT " + TASK_COLS + " FROM wf_task t WHERE t.status = 'OPEN' AND t.due_at IS NOT NULL AND t.due_at < :now"
                        + " ORDER BY t.due_at LIMIT :limit")
                .param("now", SqlTime.ts(now)).param("limit", limit).query(WorkflowRepository::mapTask).list();
    }

    private static TaskRow mapTask(ResultSet rs, int n) throws SQLException {
        return new TaskRow(rs.getObject("id", UUID.class), rs.getObject("instance_id", UUID.class), rs.getString("state_code"),
                rs.getString("name"), rs.getString("assignee_type"), rs.getString("assignee_value"), rs.getString("status"),
                SqlTime.instant(rs, "due_at"), rs.getString("completed_by"), rs.getString("completed_action"),
                rs.getString("comment"), SqlTime.instant(rs, "created_at"), SqlTime.instant(rs, "completed_at"),
                rs.getLong("version"));
    }

    public void insertHistory(UUID instanceId, UUID taskId, String from, String to, String transition, boolean automatic,
                              String actor, String comment) {
        jdbc.sql("""
                INSERT INTO wf_transition_history (id, instance_id, seq, task_id, from_state, to_state, transition_code,
                                                   automatic, actor, comment)
                VALUES (:id, :instance,
                        (SELECT COALESCE(max(seq), 0) + 1 FROM wf_transition_history WHERE instance_id = :instance),
                        :task, :from, :to, :transition, :automatic, :actor, :comment)
                """).param("id", Ids.newId()).param("instance", instanceId).param("task", taskId).param("from", from)
                .param("to", to).param("transition", transition).param("automatic", automatic).param("actor", actor)
                .param("comment", comment).update();
    }

    public List<HistoryRow> history(UUID instanceId) {
        return jdbc.sql("""
                SELECT seq, task_id, from_state, to_state, transition_code, automatic, actor, comment, occurred_at
                FROM wf_transition_history WHERE instance_id = :id ORDER BY seq
                """).param("id", instanceId)
                .query((rs, n) -> new HistoryRow(rs.getInt("seq"), rs.getObject("task_id", UUID.class), rs.getString("from_state"),
                        rs.getString("to_state"), rs.getString("transition_code"), rs.getBoolean("automatic"),
                        rs.getString("actor"), rs.getString("comment"), SqlTime.instant(rs, "occurred_at")))
                .list();
    }

    /** Người từng xử lý hoặc đang được giao (USER) task của hồ sơ. */
    public boolean isUserParticipant(UUID recordId, String subject) {
        return jdbc.sql("""
                SELECT count(*) FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id
                WHERE i.record_id = :record AND (t.completed_by = :subject OR (t.assignee_type = 'USER' AND t.assignee_value = :subject))
                """).param("record", recordId).param("subject", subject).query(Long.class).single() > 0;
    }

    public List<String> roleAssigneesOfRecord(UUID recordId) {
        return jdbc.sql("""
                SELECT DISTINCT t.assignee_value FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id
                WHERE i.record_id = :record AND t.assignee_type = 'ROLE'
                """).param("record", recordId).query(String.class).list();
    }

    public void clearDue(UUID taskId) {
        jdbc.sql("UPDATE wf_task SET due_at = NULL WHERE id = :id").param("id", taskId).update();
    }
}
