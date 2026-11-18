package vn.drwp.platform.support;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/** Correlation ID qua MDC; được TaskDecorator truyền sang listener bất đồng bộ. */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Chỉ nhận giá trị an toàn từ client (tránh log injection), không thì sinh mới. */
    public static String sanitizeOrNew(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }

    public static void set(String value) {
        MDC.put(MDC_KEY, value);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
