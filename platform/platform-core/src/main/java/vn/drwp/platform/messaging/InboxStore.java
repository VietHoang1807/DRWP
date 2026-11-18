package vn.drwp.platform.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Inbox idempotent cho consumer Kafka (at-least-once): ghi (consumer, message_id) trong schema tenant,
 * cùng transaction với hiệu ứng nghiệp vụ. Trùng thì bỏ qua.
 */
public class InboxStore {

    private final JdbcClient jdbc;

    public InboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true nếu lần đầu nhận message này (cần xử lý), false nếu trùng. */
    public boolean markReceived(String consumer, String messageId, String eventType) {
        int inserted = jdbc.sql("""
                INSERT INTO inbox_event (consumer, message_id, event_type, received_at)
                VALUES (:consumer, :messageId, :eventType, now())
                ON CONFLICT (consumer, message_id) DO NOTHING
                """)
                .param("consumer", consumer)
                .param("messageId", messageId)
                .param("eventType", eventType)
                .update();
        return inserted == 1;
    }
}
