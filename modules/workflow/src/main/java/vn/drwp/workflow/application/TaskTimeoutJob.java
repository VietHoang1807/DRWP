package vn.drwp.workflow.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenantJobRunner;
import vn.drwp.workflow.infrastructure.WorkflowRepository;

/** Kích hoạt transition onTimeout cho task quá hạn; mỗi task một transaction, ShedLock bảo đảm một node. */
@Component
@ConditionalOnRuntimeRole(RuntimeRole.SCHEDULED_WORKER)
class TaskTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutJob.class);

    private final TenantJobRunner tenants;
    private final WorkflowRepository repository;
    private final WorkflowRuntimeService runtime;

    TaskTimeoutJob(TenantJobRunner tenants, WorkflowRepository repository, WorkflowRuntimeService runtime) {
        this.tenants = tenants;
        this.repository = repository;
        this.runtime = runtime;
    }

    @Scheduled(fixedDelayString = "${drwp.workflow.timeout-scan-interval:PT1M}", initialDelayString = "PT45S")
    @SchedulerLock(name = "workflow-task-timeouts", lockAtMostFor = "PT5M")
    public void scan() {
        tenants.forEachActiveTenant("workflow-task-timeouts", tenant -> {
            Instant now = Instant.now();
            for (var task : repository.overdueTasks(now, 200)) {
                try {
                    runtime.fireTimeout(task.id(), now);
                } catch (RuntimeException e) {
                    log.error("Timeout handling failed for task {} in tenant {}", task.id(), tenant, e);
                }
            }
        });
    }
}
