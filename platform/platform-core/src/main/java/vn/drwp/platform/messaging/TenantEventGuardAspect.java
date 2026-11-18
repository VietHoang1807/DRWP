package vn.drwp.platform.messaging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.event.TenantScopedEvent;
import vn.drwp.platform.tenancy.TenantContext;

/**
 * ADR 021 — lớp kiểm tra thứ hai: listener nội bộ chỉ xử lý khi tenant context trên thread listener
 * trùng {@code tenantId} trong sự kiện. Lệch hoặc thiếu: từ chối (fail-closed), publication giữ trạng thái
 * chưa hoàn tất để điều tra, ghi log bảo mật.
 */
@Aspect
public class TenantEventGuardAspect {

    private static final Logger security = LoggerFactory.getLogger("SECURITY");

    @Around("@annotation(org.springframework.modulith.events.ApplicationModuleListener)")
    public Object guard(ProceedingJoinPoint pjp) throws Throwable {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof TenantScopedEvent event) {
                String bound = TenantContext.current().map(Object::toString).orElse(null);
                if (bound == null || !bound.equals(event.tenantId())) {
                    security.error("Rejected internal event {} for tenant {} on thread bound to {}",
                            event.getClass().getSimpleName(), event.tenantId(), bound);
                    throw new TenantIsolationException("Event tenant does not match tenant context");
                }
            }
        }
        return pjp.proceed();
    }
}
