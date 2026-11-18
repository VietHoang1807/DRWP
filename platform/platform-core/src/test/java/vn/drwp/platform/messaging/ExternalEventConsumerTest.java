package vn.drwp.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;
import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.TenantPlacement;
import vn.drwp.platform.tenancy.TenantPlacementLookup;
import vn.drwp.platform.tenancy.TenantStatus;

/** ADR 022: tenant của sự kiện Kafka lấy từ schema nguồn, không tin payload. */
class ExternalEventConsumerTest {

    private final TenantPlacementLookup lookup = mock(TenantPlacementLookup.class);
    private ExternalEventConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties(Duration.ofMinutes(5), 100, Duration.ofDays(7), Duration.ofDays(7),
                "drwp\\.events\\..*", "g", "__debezium_source");
        consumer = new ExternalEventConsumer(lookup, List.of(), mock(InboxStore.class), mock(TransactionTemplate.class),
                new Json(JsonMapper.builder().build()), props);
        when(lookup.findBySchema("cluster-a", "tenant_alpha")).thenReturn(Optional.of(
                new TenantPlacement(TenantId.of("alpha"), "cluster-a", "default", "tenant_alpha", 1, TenantStatus.ACTIVE)));
    }

    private static ConsumerRecord<String, String> record(String payload, String sourceSchema) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("drwp.events.record", 0, 0, "agg-1", payload);
        if (sourceSchema != null) {
            r.headers().add("__debezium_source", ("{\"name\":\"cluster-a\",\"schema\":\"" + sourceSchema + "\",\"table\":\"outbox_event\"}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        r.headers().add("id", "\"m-1\"".getBytes(StandardCharsets.UTF_8));
        r.headers().add("eventType", "\"RecordChanged\"".getBytes(StandardCharsets.UTF_8));
        return r;
    }

    @Test
    void acceptsEventWhoseTenantMatchesSourceSchema() {
        ExternalEvent e = consumer.verify(record("{\"tenantId\":\"alpha\",\"recordId\":\"r\"}", "tenant_alpha"));
        assertThat(e.tenantId()).isEqualTo(TenantId.of("alpha"));
        assertThat(e.messageId()).isEqualTo("m-1");
        assertThat(e.type()).isEqualTo("RecordChanged");
    }

    @Test
    void rejectsForgedTenantInPayload() {
        assertThatThrownBy(() -> consumer.verify(record("{\"tenantId\":\"beta\"}", "tenant_alpha")))
                .isInstanceOf(TenantProvenanceException.class);
    }

    @Test
    void rejectsUnknownSchemaOrMissingSource() {
        assertThatThrownBy(() -> consumer.verify(record("{\"tenantId\":\"alpha\"}", "tenant_ghost")))
                .isInstanceOf(TenantProvenanceException.class);
        assertThatThrownBy(() -> consumer.verify(record("{\"tenantId\":\"alpha\"}", null)))
                .isInstanceOf(TenantProvenanceException.class);
    }
}
