package vn.drwp.platform.runtime;

/**
 * ADR 018: cùng artifact business-app, bật vai trò bằng {@code drwp.runtime.roles} (Spring profile đặt sẵn).
 */
public enum RuntimeRole {
    /** HTTP API. Tắt Kafka listener. */
    API("api"),
    /** Kafka listener (search, OCR, notification, import lô). Chỉ mở actuator. */
    EVENT_WORKER("event-worker"),
    /** Job định kỳ, khóa bằng ShedLock. */
    SCHEDULED_WORKER("scheduled-worker");

    private final String key;

    RuntimeRole(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
