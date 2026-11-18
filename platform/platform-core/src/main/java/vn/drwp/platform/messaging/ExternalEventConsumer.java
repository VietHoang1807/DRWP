package vn.drwp.platform.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.tenancy.TenantPlacement;
import vn.drwp.platform.tenancy.TenantPlacementLookup;

/**
 * Consumer Kafka của vai trò event-worker.
 * <ol>
 *   <li>ADR 022: tenant được xác định từ schema nguồn trong metadata Debezium (header {@code __debezium_source}),
 *       tra {@code tenant_placement} ra tenant, so với {@code tenantId} trong payload và header. Lệch: DLT + log bảo mật.</li>
 *   <li>Đặt tenant context theo từng message, dọn trong finally.</li>
 *   <li>Inbox idempotent cùng transaction với hiệu ứng của handler.</li>
 * </ol>
 */
@Component
@ConditionalOnRuntimeRole(RuntimeRole.EVENT_WORKER)
class ExternalEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExternalEventConsumer.class);
    private static final Logger security = LoggerFactory.getLogger("SECURITY");

    private final TenantPlacementLookup placements;
    private final List<ExternalEventHandler> handlers;
    private final InboxStore inbox;
    private final TransactionTemplate tx;
    private final Json json;
    private final MessagingProperties properties;

    @Autowired
    ExternalEventConsumer(TenantPlacementLookup placements, ObjectProvider<ExternalEventHandler> handlers, InboxStore inbox,
                          TransactionTemplate tx, Json json, MessagingProperties properties) {
        this(placements, handlers.orderedStream().toList(), inbox, tx, json, properties);
    }

    ExternalEventConsumer(TenantPlacementLookup placements, List<ExternalEventHandler> handlers, InboxStore inbox,
                          TransactionTemplate tx, Json json, MessagingProperties properties) {
        this.placements = placements;
        this.handlers = handlers;
        this.inbox = inbox;
        this.tx = tx;
        this.json = json;
        this.properties = properties;
    }

    @KafkaListener(topicPattern = "${drwp.events.topic-pattern:drwp\\.events\\..*}",
            groupId = "${drwp.events.consumer-group:drwp-event-worker}")
    public void onMessage(ConsumerRecord<String, String> record) {
        ExternalEvent event = verify(record);
        TenantContext.runAs(event.tenantId(), () -> {
            for (ExternalEventHandler handler : handlers) {
                if (!handler.supports(event.type())) {
                    continue;
                }
                tx.executeWithoutResult(status -> {
                    if (inbox.markReceived(handler.consumerName(), event.messageId(), event.type())) {
                        handler.handle(event);
                    } else {
                        log.debug("Duplicate message {} for consumer {} ignored", event.messageId(), handler.consumerName());
                    }
                });
            }
        });
    }

    ExternalEvent verify(ConsumerRecord<String, String> record) {
        String sourceRaw = header(record, properties.sourceHeader());
        if (sourceRaw == null) {
            throw provenance(record, "missing source metadata");
        }
        Map<String, Object> source = json.readMap(sourceRaw);
        String schema = (String) source.get("schema");
        String cluster = (String) source.get("name");
        TenantPlacement placement = placements.findBySchema(cluster, schema)
                .orElseThrow(() -> provenance(record, "unknown source schema " + cluster + "/" + schema));
        TenantId tenant = placement.tenantId();

        Map<String, Object> payload = json.readMap(record.value());
        Object payloadTenant = payload.get("tenantId");
        String headerTenant = header(record, "tenantId");
        if (!tenant.value().equals(payloadTenant) || (headerTenant != null && !tenant.value().equals(headerTenant))) {
            throw provenance(record, "tenantId mismatch: source=" + tenant + " payload=" + payloadTenant + " header=" + headerTenant);
        }
        String messageId = header(record, "id");
        if (messageId == null) {
            messageId = String.valueOf(payload.get("eventId"));
        }
        String type = header(record, "eventType");
        String version = header(record, "eventVersion");
        return new ExternalEvent(messageId, type, version == null ? 1 : Integer.parseInt(version), tenant,
                header(record, "aggregateType"), record.key(), payload);
    }

    private TenantProvenanceException provenance(ConsumerRecord<String, String> record, String reason) {
        security.error("Kafka event provenance check failed topic={} partition={} offset={}: {}",
                record.topic(), record.partition(), record.offset(), reason);
        return new TenantProvenanceException(reason);
    }

    /** Header từ JsonConverter là JSON: chuỗi có dấu nháy, struct là object. */
    private String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null || h.value() == null) {
            return null;
        }
        String raw = new String(h.value(), StandardCharsets.UTF_8).trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return json.read(raw, String.class);
        }
        return raw;
    }
}
