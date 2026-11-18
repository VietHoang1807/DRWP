package vn.drwp.platform.tenancy;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * ADR 021: chụp tenant context (và MDC: correlationId...) tại thread phát sự kiện, đặt lại trên thread
 * listener bất đồng bộ, dọn trong finally. Spring Boot tự gắn {@link TaskDecorator} vào applicationTaskExecutor,
 * executor mà {@code @ApplicationModuleListener} (@Async) sử dụng.
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        TenantContext.Snapshot snapshot = TenantContext.snapshot();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                TenantContext.callWith(snapshot, () -> {
                    runnable.run();
                    return null;
                });
            } finally {
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
