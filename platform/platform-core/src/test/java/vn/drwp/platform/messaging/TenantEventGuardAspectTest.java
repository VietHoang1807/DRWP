package vn.drwp.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.TenantContext;

/** ADR 021: listener từ chối khi tenant context và tenantId của sự kiện lệch nhau. */
class TenantEventGuardAspectTest {

    private final TenantEventGuardAspect aspect = new TenantEventGuardAspect();

    private static BusinessAuditEvent event(String tenant) {
        return new BusinessAuditEvent(tenant, "a", "X", "t", "1", BusinessAuditEvent.Outcome.SUCCESS, Map.of(), null, null);
    }

    @Test
    void proceedsWhenTenantMatches() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{event("alpha")});
        when(pjp.proceed()).thenReturn("ok");
        Object result = TenantContext.callAs(TenantId.of("alpha"), () -> {
            try {
                return aspect.guard(pjp);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void rejectsMismatchAndMissingContext() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{event("beta")});
        assertThatThrownBy(() -> TenantContext.runAs(TenantId.of("alpha"), () -> {
            try {
                aspect.guard(pjp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        })).isInstanceOf(TenantIsolationException.class);
        assertThatThrownBy(() -> aspect.guard(pjp)).isInstanceOf(TenantIsolationException.class);
        verify(pjp, never()).proceed();
    }
}
