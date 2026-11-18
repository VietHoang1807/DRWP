package vn.drwp.platform.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;

/**
 * ADR 020: ghi sự kiện externalized vào {@code outbox_event} của schema tenant, CÙNG transaction với
 * aggregate. Debezium đọc WAL và đẩy sang Kafka (EventRouter: aggregatetype -> topic).
 * Payload phải tối thiểu, không chứa PII thừa (tài liệu 01 mục 8).
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final Json json;

    public OutboxWriter(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID append(String aggregateType, String aggregateId, String eventType, int eventVersion, Map<String, ?> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox append requires an active transaction");
        }
        UUID id = Ids.newId();
        String tenantId = TenantContext.require().value();
        // tenantId luôn có trong payload để worker đối chiếu với schema nguồn (ADR 022)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id.toString());
        body.put("tenantId", tenantId);
        body.putAll(payload);
        body.put("tenantId", tenantId);
        jdbc.sql("""
                INSERT INTO outbox_event (id, aggregatetype, aggregateid, type, event_version, tenant_id,
                                          correlation_id, payload, occurred_at)
                VALUES (:id, :aggregateType, :aggregateId, :type, :version, :tenantId, :correlationId,
                        CAST(:payload AS jsonb), :occurredAt)
                """)
                .param("id", id)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("type", eventType)
                .param("version", eventVersion)
                .param("tenantId", tenantId)
                .param("correlationId", CorrelationId.current())
                .param("payload", json.write(body))
                .param("occurredAt", Timestamp.from(Instant.now()))
                .update();
        return id;
    }
}
