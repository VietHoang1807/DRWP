package vn.drwp.platform.messaging;

/**
 * Handler cho sự kiện Kafka trong vai trò event-worker (search indexing, OCR, notification, import lô...).
 * Được gọi trong tenant context và transaction của tenant; inbox đã khử trùng lặp.
 */
public interface ExternalEventHandler {

    /** Tên consumer ổn định, dùng làm khóa inbox. */
    String consumerName();

    boolean supports(String eventType);

    void handle(ExternalEvent event);
}
