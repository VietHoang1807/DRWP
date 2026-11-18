package vn.drwp.notification.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.support.Ids;
import vn.drwp.platform.support.SqlTime;

@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NotificationRow(UUID id, String recipient, String type, String title, String body, String refType,
                                  String refId, Instant readAt, Instant createdAt) {
    }

    /** @return id mới, hoặc null nếu đã tồn tại (trùng sự kiện). */
    public UUID insertIfAbsent(String recipient, String type, String title, String body, String refType, String refId) {
        UUID id = Ids.newId();
        int n = jdbc.sql("""
                INSERT INTO noti_notification (id, recipient, type, title, body, ref_type, ref_id)
                VALUES (:id, :recipient, :type, :title, :body, :refType, :refId)
                ON CONFLICT (recipient, type, ref_id) DO NOTHING
                """).param("id", id).param("recipient", recipient).param("type", type).param("title", title)
                .param("body", body).param("refType", refType).param("refId", refId).update();
        return n == 1 ? id : null;
    }

    public List<NotificationRow> list(String recipient, boolean unreadOnly, int limit, long offset) {
        return jdbc.sql("SELECT id, recipient, type, title, body, ref_type, ref_id, read_at, created_at FROM noti_notification"
                        + " WHERE recipient = :r" + (unreadOnly ? " AND read_at IS NULL" : "")
                        + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                .param("r", recipient).param("limit", limit).param("offset", offset)
                .query((rs, n) -> new NotificationRow(rs.getObject("id", UUID.class), rs.getString("recipient"),
                        rs.getString("type"), rs.getString("title"), rs.getString("body"), rs.getString("ref_type"),
                        rs.getString("ref_id"), SqlTime.instant(rs, "read_at"), SqlTime.instant(rs, "created_at")))
                .list();
    }

    public long count(String recipient, boolean unreadOnly) {
        return jdbc.sql("SELECT count(*) FROM noti_notification WHERE recipient = :r" + (unreadOnly ? " AND read_at IS NULL" : ""))
                .param("r", recipient).query(Long.class).single();
    }

    /** Chỉ chủ thông báo mới đánh dấu được (điều kiện recipient trong WHERE). */
    public int markRead(UUID id, String recipient) {
        return jdbc.sql("UPDATE noti_notification SET read_at = now() WHERE id = :id AND recipient = :r AND read_at IS NULL")
                .param("id", id).param("r", recipient).update();
    }

    public int markAllRead(String recipient) {
        return jdbc.sql("UPDATE noti_notification SET read_at = now() WHERE recipient = :r AND read_at IS NULL")
                .param("r", recipient).update();
    }
}
