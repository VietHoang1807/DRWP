package vn.drwp.platform.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("drwp.events")
public record MessagingProperties(
        /** Chỉ phát lại publication chưa hoàn tất cũ hơn ngưỡng này (tránh tranh chấp với listener đang chạy). */
        @DefaultValue("5m") Duration replayOlderThan,
        /** Cảnh báo khi số publication chưa hoàn tất của một tenant vượt ngưỡng (ADR 016, chốt ở Phase 4). */
        @DefaultValue("100") int registryBacklogWarnThreshold,
        /** Giữ outbox đã phát bao lâu trước khi dọn. */
        @DefaultValue("7d") Duration outboxRetention,
        /** Giữ publication đã hoàn tất bao lâu. */
        @DefaultValue("7d") Duration completedPublicationRetention,
        @DefaultValue("drwp\\.events\\..*") String topicPattern,
        @DefaultValue("drwp-event-worker") String consumerGroup,
        /** Tên header Debezium chứa struct source (HeaderFrom SMT). */
        @DefaultValue("__debezium_source") String sourceHeader) {
}
