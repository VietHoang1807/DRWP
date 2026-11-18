package vn.drwp.platform.tenancy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Trong lúc khởi động và tắt, framework (Spring Modulith dò loại DB; registry liệt kê publication còn dở khi
 * destroy) có thể xin connection mà không có tenant: khi đó nhận connection trỏ schema "no-tenant" (không có bảng
 * nghiệp vụ, {@code event_publication} rỗng và cấm ghi). Giữa {@link ApplicationReadyEvent} và
 * {@link ContextClosedEvent} là strict: không có tenant context thì ném lỗi.
 */
public class TenancyStartupGuard implements ApplicationListener<ApplicationEvent> {

    private final boolean strictAfterStartup;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TenancyStartupGuard(boolean strictAfterStartup) {
        this.strictAfterStartup = strictAfterStartup;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationReadyEvent) {
            running.set(true);
        } else if (event instanceof ContextClosedEvent) {
            running.set(false);
        }
    }

    public boolean isStrict() {
        return strictAfterStartup && running.get();
    }
}
