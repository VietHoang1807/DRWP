package vn.drwp.platform.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenantJobRunner;

/**
 * ADR 019: registry nằm trong từng schema tenant nên phải phát lại theo từng tenant:
 * lặp tenant ACTIVE, đặt tenant context, gọi phát lại, dọn context trong finally (TenantJobRunner).
 * Kèm cảnh báo backlog (ADR 016) và dọn outbox/publication cũ.
 */
@Component
@ConditionalOnRuntimeRole(RuntimeRole.SCHEDULED_WORKER)
class EventPublicationMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationMaintenanceJob.class);

    private final TenantJobRunner tenants;
    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final JdbcClient jdbc;
    private final MessagingProperties properties;

    EventPublicationMaintenanceJob(TenantJobRunner tenants, IncompleteEventPublications incomplete,
                                   CompletedEventPublications completed, JdbcClient jdbc, MessagingProperties properties) {
        this.tenants = tenants;
        this.incomplete = incomplete;
        this.completed = completed;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${drwp.events.replay-interval:PT1M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "event-publication-replay", lockAtMostFor = "PT5M")
    public void replayIncompletePublications() {
        tenants.forEachActiveTenant("event-publication-replay", tenant -> {
            Long backlog = jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL")
                    .query(Long.class).single();
            if (backlog > properties.registryBacklogWarnThreshold()) {
                log.warn("Tenant {} has {} incomplete event publications (threshold {})",
                        tenant, backlog, properties.registryBacklogWarnThreshold());
            }
            if (backlog > 0) {
                incomplete.resubmitIncompletePublicationsOlderThan(properties.replayOlderThan());
            }
        });
    }

    @Scheduled(cron = "${drwp.events.cleanup-cron:0 15 2 * * *}")
    @SchedulerLock(name = "event-housekeeping", lockAtMostFor = "PT30M")
    public void housekeeping() {
        tenants.forEachActiveTenant("event-housekeeping", tenant -> {
            completed.deletePublicationsOlderThan(properties.completedPublicationRetention());
            int removed = jdbc.sql("DELETE FROM outbox_event WHERE occurred_at < now() - CAST(:retention AS interval)")
                    .param("retention", properties.outboxRetention().toSeconds() + " seconds")
                    .update();
            if (removed > 0) {
                log.info("Tenant {}: removed {} outbox rows", tenant, removed);
            }
        });
    }
}
