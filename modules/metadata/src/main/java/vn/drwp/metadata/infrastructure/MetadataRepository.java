package vn.drwp.metadata.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.metadata.domain.FormVersionStatus;
import vn.drwp.platform.json.Json;
import vn.drwp.platform.support.SqlTime;

@Repository
public class MetadataRepository {

    private final JdbcClient jdbc;
    private final Json json;

    public MetadataRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record FormRow(UUID id, String code, String name, String description, String status, String createdBy,
                          Instant createdAt) {
    }

    public record VersionRow(UUID id, UUID formId, String formCode, String formName, int versionNo,
                             FormVersionStatus status, Map<String, Object> schema, String checksum, String createdBy,
                             Instant createdAt, String publishedBy, Instant publishedAt) {
    }

    private static final String VERSION_SELECT = """
            SELECT v.id, v.form_id, f.code, f.name, v.version_no, v.status, v.schema::text AS schema, v.checksum,
                   v.created_by, v.created_at, v.published_by, v.published_at
            FROM meta_form_version v JOIN meta_form_definition f ON f.id = v.form_id
            """;

    public Optional<FormRow> findForm(String code) {
        return jdbc.sql("SELECT id, code, name, description, status, created_by, created_at FROM meta_form_definition WHERE code = :code")
                .param("code", code).query(MetadataRepository::mapForm).optional();
    }

    public List<FormRow> forms() {
        return jdbc.sql("SELECT id, code, name, description, status, created_by, created_at FROM meta_form_definition ORDER BY code")
                .query(MetadataRepository::mapForm).list();
    }

    private static FormRow mapForm(ResultSet rs, int n) throws SQLException {
        return new FormRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), rs.getString("created_by"), SqlTime.instant(rs, "created_at"));
    }

    public void insertForm(UUID id, String code, String name, String description, String by) {
        jdbc.sql("""
                INSERT INTO meta_form_definition (id, code, name, description, created_by)
                VALUES (:id, :code, :name, :description, :by)
                """).param("id", id).param("code", code).param("name", name).param("description", description)
                .param("by", by).update();
    }

    public int updateForm(String code, String name, String description, String status) {
        return jdbc.sql("""
                UPDATE meta_form_definition SET name = :name, description = :description, status = :status, updated_at = now()
                WHERE code = :code
                """).param("name", name).param("description", description).param("status", status).param("code", code).update();
    }

    public void insertVersion(UUID id, UUID formId, int versionNo, Map<String, Object> schema, String by) {
        jdbc.sql("""
                INSERT INTO meta_form_version (id, form_id, version_no, status, schema, created_by)
                VALUES (:id, :formId, :versionNo, 'DRAFT', CAST(:schema AS jsonb), :by)
                """).param("id", id).param("formId", formId).param("versionNo", versionNo)
                .param("schema", json.write(schema)).param("by", by).update();
    }

    public int updateDraftSchema(UUID versionId, Map<String, Object> schema) {
        return jdbc.sql("""
                UPDATE meta_form_version SET schema = CAST(:schema AS jsonb), updated_at = now()
                WHERE id = :id AND status = 'DRAFT'
                """).param("schema", json.write(schema)).param("id", versionId).update();
    }

    public int publish(UUID versionId, String checksum, String by) {
        return jdbc.sql("""
                UPDATE meta_form_version SET status = 'PUBLISHED', checksum = :checksum, published_by = :by,
                       published_at = now(), updated_at = now()
                WHERE id = :id AND status = 'DRAFT'
                """).param("checksum", checksum).param("by", by).param("id", versionId).update();
    }

    public int deprecate(UUID versionId) {
        return jdbc.sql("UPDATE meta_form_version SET status = 'DEPRECATED', updated_at = now() WHERE id = :id AND status = 'PUBLISHED'")
                .param("id", versionId).update();
    }

    public int deleteDraft(UUID versionId) {
        return jdbc.sql("DELETE FROM meta_form_version WHERE id = :id AND status = 'DRAFT'").param("id", versionId).update();
    }

    public Optional<VersionRow> version(UUID id) {
        return jdbc.sql(VERSION_SELECT + " WHERE v.id = :id").param("id", id).query(this::mapVersion).optional();
    }

    public Optional<VersionRow> version(String formCode, int versionNo) {
        return jdbc.sql(VERSION_SELECT + " WHERE f.code = :code AND v.version_no = :no")
                .param("code", formCode).param("no", versionNo).query(this::mapVersion).optional();
    }

    public List<VersionRow> versions(String formCode) {
        return jdbc.sql(VERSION_SELECT + " WHERE f.code = :code ORDER BY v.version_no DESC")
                .param("code", formCode).query(this::mapVersion).list();
    }

    public Optional<VersionRow> latestPublished(String formCode) {
        return jdbc.sql(VERSION_SELECT + " WHERE f.code = :code AND f.status = 'ACTIVE' AND v.status = 'PUBLISHED' ORDER BY v.version_no DESC LIMIT 1")
                .param("code", formCode).query(this::mapVersion).optional();
    }

    public int maxVersionNo(UUID formId) {
        return jdbc.sql("SELECT COALESCE(max(version_no), 0) FROM meta_form_version WHERE form_id = :id")
                .param("id", formId).query(Integer.class).single();
    }

    private VersionRow mapVersion(ResultSet rs, int n) throws SQLException {
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("form_id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getInt("version_no"), FormVersionStatus.valueOf(rs.getString("status")),
                json.readMap(rs.getString("schema")), rs.getString("checksum"), rs.getString("created_by"),
                SqlTime.instant(rs, "created_at"), rs.getString("published_by"), SqlTime.instant(rs, "published_at"));
    }
}
