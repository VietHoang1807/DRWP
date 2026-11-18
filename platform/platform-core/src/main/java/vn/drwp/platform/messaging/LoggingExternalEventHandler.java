package vn.drwp.platform.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;

/**
 * Handler mẫu của event-worker: ghi log mọi sự kiện đã qua kiểm tra provenance + inbox.
 * Phase 6 thay bằng indexer Elasticsearch / read model reporting; Phase 7 thêm OCR worker.
 */
@Component
@ConditionalOnRuntimeRole(RuntimeRole.EVENT_WORKER)
class LoggingExternalEventHandler implements ExternalEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingExternalEventHandler.class);

    @Override
    public String consumerName() {
        return "event-log";
    }

    @Override
    public boolean supports(String eventType) {
        return true;
    }

    @Override
    public void handle(ExternalEvent event) {
        log.info("External event {} v{} {}:{} tenant={}", event.type(), event.version(), event.aggregateType(),
                event.aggregateId(), event.tenantId());
    }
}
