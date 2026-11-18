package vn.drwp.file.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.support.SqlTime;

@Repository
public class FileRepository {

    private final JdbcClient jdbc;

    public FileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record FileRow(UUID id, UUID recordId, String originalName, String contentType, long sizeBytes, String sha256,
                          String storageKey, String status, String scanResult, String uploadedBy, Instant uploadedAt) {
    }

    private static final String COLS = "id, record_id, original_name, content_type, size_bytes, sha256, storage_key, status, scan_result, uploaded_by, uploaded_at";

    public void insert(FileRow f) {
        jdbc.sql("""
                INSERT INTO file_metadata (id, record_id, original_name, content_type, size_bytes, sha256, storage_key, status,
                                           scan_result, uploaded_by, uploaded_at)
                VALUES (:id, :record, :name, :type, :size, :sha, :key, :status, :scan, :by, :at)
                """).param("id", f.id()).param("record", f.recordId()).param("name", f.originalName())
                .param("type", f.contentType()).param("size", f.sizeBytes()).param("sha", f.sha256())
                .param("key", f.storageKey()).param("status", f.status()).param("scan", f.scanResult())
                .param("by", f.uploadedBy()).param("at", SqlTime.ts(f.uploadedAt())).update();
    }

    public Optional<FileRow> find(UUID id) {
        return jdbc.sql("SELECT " + COLS + " FROM file_metadata WHERE id = :id").param("id", id).query(FileRepository::map).optional();
    }

    public List<FileRow> byRecord(UUID recordId) {
        return jdbc.sql("SELECT " + COLS + " FROM file_metadata WHERE record_id = :r AND status = 'AVAILABLE' ORDER BY uploaded_at")
                .param("r", recordId).query(FileRepository::map).list();
    }

    public int markDeleted(UUID id) {
        return jdbc.sql("UPDATE file_metadata SET status = 'DELETED', deleted_at = now() WHERE id = :id AND status = 'AVAILABLE'")
                .param("id", id).update();
    }

    private static FileRow map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new FileRow(rs.getObject("id", UUID.class), rs.getObject("record_id", UUID.class), rs.getString("original_name"),
                rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("sha256"), rs.getString("storage_key"),
                rs.getString("status"), rs.getString("scan_result"), rs.getString("uploaded_by"), SqlTime.instant(rs, "uploaded_at"));
    }
}
