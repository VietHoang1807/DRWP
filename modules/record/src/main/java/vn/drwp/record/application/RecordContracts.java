package vn.drwp.record.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.messaging.OutboxWriter;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.record.api.RecordAttachmentPolicy;
import vn.drwp.record.api.RecordLifecycle;
import vn.drwp.record.api.RecordOutcome;
import vn.drwp.record.api.RecordReference;
import vn.drwp.record.api.RecordReferenceReader;
import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.api.RecordStatusChanged;
import vn.drwp.record.domain.DynamicRecord;
import vn.drwp.record.infrastructure.RecordRepository;

/** Cài đặt các public contract của module Record. */
@Component
class RecordContracts implements RecordReferenceReader, RecordLifecycle, RecordAttachmentPolicy {

    private final RecordRepository repository;
    private final PermissionChecker permissions;
    private final ApplicationEventPublisher events;
    private final OutboxWriter outbox;

    RecordContracts(RecordRepository repository, PermissionChecker permissions, ApplicationEventPublisher events,
                    OutboxWriter outbox) {
        this.repository = repository;
        this.permissions = permissions;
        this.events = events;
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordReference> find(UUID recordId) {
        return repository.find(recordId).map(r -> new RecordReference(r.id(), r.recordCode(), r.formCode(),
                r.formVersionId(), r.status(), r.owner(), r.mutableDataCopy(), r.version()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markInProgress(UUID recordId, UUID workflowInstanceId) {
        DynamicRecord r = repository.findForUpdate(recordId).orElseThrow(() -> new NotFoundException("Record", recordId));
        RecordStatus from = r.status();
        r.markInProgress(workflowInstanceId);
        repository.update(r);
        repository.insertRevision(r.id(), "WORKFLOW_STARTED", r.status(), r.mutableDataCopy(), "system:workflow");
        statusChanged(r, from, "system:workflow");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID recordId, RecordOutcome outcome, String actor) {
        DynamicRecord r = repository.findForUpdate(recordId).orElseThrow(() -> new NotFoundException("Record", recordId));
        RecordStatus from = r.status();
        r.complete(outcome, Instant.now());
        repository.update(r);
        repository.insertRevision(r.id(), "WORKFLOW_" + outcome.name(), r.status(), r.mutableDataCopy(), actor);
        statusChanged(r, from, actor);
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor, "RECORD_" + outcome.name(),
                "record", r.id().toString(), BusinessAuditEvent.Outcome.SUCCESS,
                Map.of("recordCode", r.recordCode()), CorrelationId.current(), null));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAttach(UUID recordId, String subject) {
        return repository.find(recordId)
                .map(r -> (r.isOwner(subject) && r.status().editable())
                        || permissions.isAllowed(subject, RecordService.resource(r.formCode()), Action.UPDATE))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(UUID recordId, String subject) {
        return repository.find(recordId)
                .map(r -> r.isOwner(subject) || permissions.isAllowed(subject, RecordService.resource(r.formCode()), Action.VIEW))
                .orElse(false);
    }

    private void statusChanged(DynamicRecord r, RecordStatus from, String actor) {
        events.publishEvent(new RecordStatusChanged(TenantContext.require().value(), r.id(), r.recordCode(), r.formCode(),
                from, r.status(), r.owner(), actor, Instant.now()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordId", r.id().toString());
        payload.put("recordCode", r.recordCode());
        payload.put("formCode", r.formCode());
        payload.put("status", r.status().name());
        payload.put("changeType", "STATUS_" + r.status().name());
        outbox.append("record", r.id().toString(), "RecordChanged", 1, payload);
    }
}
