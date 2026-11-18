package vn.drwp.workflow.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.identity.api.RoleDirectory;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.kernel.security.ResourceCode;
import vn.drwp.platform.messaging.OutboxWriter;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.record.api.RecordLifecycle;
import vn.drwp.record.api.RecordOutcome;
import vn.drwp.record.api.RecordReference;
import vn.drwp.record.api.RecordReferenceReader;
import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.api.RecordSubmitted;
import vn.drwp.workflow.api.TaskAssigned;
import vn.drwp.workflow.api.WorkflowCompleted;
import vn.drwp.workflow.domain.AssignmentRule;
import vn.drwp.workflow.domain.StateDef;
import vn.drwp.workflow.domain.StateMachine;
import vn.drwp.workflow.domain.StateMachine.Advance;
import vn.drwp.workflow.domain.StateMachine.Step;
import vn.drwp.workflow.domain.TransitionDef;
import vn.drwp.workflow.domain.WorkflowSpec;
import vn.drwp.workflow.infrastructure.WorkflowRepository;
import vn.drwp.workflow.infrastructure.WorkflowRepository.DefinitionRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.InstanceRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.TaskRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.VersionRow;

/**
 * Thực thi quy trình. Pipeline chuyển trạng thái (tài liệu 01 mục 6):
 * tenant + actor -> nạp instance/version -> khóa lạc quan + khóa dòng -> assignment -> bitmask -> guard
 * -> đổi trạng thái/task/lịch sử -> cập nhật hồ sơ -> outbox -> commit. Bị từ chối thì ghi audit DENIED
 * trong transaction riêng (tài liệu 03 Phase 4: "denied transitions audited").
 */
@Service
public class WorkflowRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeService.class);
    static final String SYSTEM = "system:workflow";

    private final WorkflowRepository repository;
    private final SpecCache specs;
    private final RecordReferenceReader records;
    private final RecordLifecycle recordLifecycle;
    private final PermissionChecker permissions;
    private final RoleDirectory roles;
    private final ApplicationEventPublisher events;
    private final OutboxWriter outbox;
    private final TransactionTemplate auditTx;

    WorkflowRuntimeService(WorkflowRepository repository, SpecCache specs, RecordReferenceReader records,
                           RecordLifecycle recordLifecycle, PermissionChecker permissions, RoleDirectory roles,
                           ApplicationEventPublisher events, OutboxWriter outbox, PlatformTransactionManager tm) {
        this.repository = repository;
        this.specs = specs;
        this.records = records;
        this.recordLifecycle = recordLifecycle;
        this.permissions = permissions;
        this.roles = roles;
        this.events = events;
        this.outbox = outbox;
        this.auditTx = new TransactionTemplate(tm);
        this.auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    static String resource(String definitionCode) {
        return ResourceCode.of("wf", definitionCode);
    }

    // ------------------------------------------------------------------ khởi tạo (ADR 016)

    /**
     * Sau commit của Record, transaction riêng; registry phát lại nếu lỗi. Idempotent theo submissionId.
     */
    @ApplicationModuleListener
    public void onRecordSubmitted(RecordSubmitted event) {
        if (repository.instanceExistsForTrigger(event.submissionId())) {
            log.debug("Workflow already started for submission {}", event.submissionId());
            return;
        }
        Optional<DefinitionRow> definition = repository.activeDefinitionForForm(event.formCode());
        Optional<VersionRow> version = definition.flatMap(d -> repository.latestPublished(d.id()));
        if (version.isEmpty()) {
            log.info("No published workflow bound to form {}; record {} stays SUBMITTED", event.formCode(), event.recordCode());
            audit(SYSTEM, "WORKFLOW_NOT_CONFIGURED", "record", event.recordId().toString(), BusinessAuditEvent.Outcome.FAILED,
                    Map.of("formCode", event.formCode()));
            return;
        }
        RecordReference record = records.find(event.recordId())
                .orElseThrow(() -> new NotFoundException("Record", event.recordId()));
        if (record.status() != RecordStatus.SUBMITTED) {
            log.info("Record {} is {} (not SUBMITTED); workflow not started", record.recordCode(), record.status());
            return;
        }
        DefinitionRow def = definition.get();
        VersionRow ver = version.get();
        WorkflowSpec spec = specs.get(ver.id());
        Advance advance = StateMachine.start(spec, record.data());
        Instant now = Instant.now();
        UUID instanceId = Ids.newId();
        InstanceRow instance = new InstanceRow(instanceId, def.id(), def.code(), ver.id(), ver.versionNo(), record.id(),
                record.recordCode(), record.owner(), advance.ended() ? "COMPLETED" : "RUNNING", advance.currentState(),
                advance.ended() ? advance.outcome().name() : null, event.submittedBy(), now, advance.ended() ? now : null, 0);
        repository.insertInstance(instance, event.submissionId());
        repository.insertHistory(instanceId, null, null, spec.initialState(), null, true, SYSTEM, null);
        for (Step step : advance.steps()) {
            repository.insertHistory(instanceId, null, step.from(), step.to(), step.transitionCode(), true, SYSTEM, null);
        }
        recordLifecycle.markInProgress(record.id(), instanceId);
        if (advance.ended()) {
            finish(instance, advance, SYSTEM, null, now);
        } else {
            createTask(instance, spec, advance.currentState(), record, now);
        }
        outbox.append("workflow", instanceId.toString(), "WorkflowStarted", 1, Map.of(
                "instanceId", instanceId.toString(), "recordId", record.id().toString(),
                "definitionCode", def.code(), "versionNo", ver.versionNo(), "state", advance.currentState()));
        audit(event.submittedBy(), "WORKFLOW_STARTED", "workflow", instanceId.toString(), BusinessAuditEvent.Outcome.SUCCESS,
                Map.of("definition", def.code(), "version", String.valueOf(ver.versionNo()), "record", record.recordCode()));
    }

    // ------------------------------------------------------------------ chuyển trạng thái

    @Transactional
    public void executeTransition(Actor actor, UUID taskId, String transitionCode, String comment, Long expectedInstanceVersion) {
        TaskRow task = repository.task(taskId, true).orElseThrow(() -> new NotFoundException("Task", taskId));
        InstanceRow instance = repository.instance(task.instanceId(), true).orElseThrow();
        if (!"RUNNING".equals(instance.status()) || !"OPEN".equals(task.status())) {
            throw new InvalidStateException("Task is no longer open");
        }
        if (!task.stateCode().equals(instance.currentState())) {
            throw new InvalidStateException("Task does not belong to the current state");
        }
        if (expectedInstanceVersion != null && expectedInstanceVersion != instance.version()) {
            throw new ConflictException("STALE_WORKFLOW", "Workflow version is " + instance.version());
        }
        WorkflowSpec spec = specs.get(instance.versionId());
        TransitionDef transition = StateMachine.transition(spec, instance.currentState(), transitionCode);

        if (!isAssignee(actor.subject(), task)) {
            deny(actor, instance, task, transitionCode, "NOT_ASSIGNEE");
        }
        if (!permissions.isAllowed(actor.subject(), resource(instance.definitionCode()), transition.permission())) {
            deny(actor, instance, task, transitionCode, "MISSING_PERMISSION_" + transition.permission());
        }
        if (transition.commentRequired() && (comment == null || comment.isBlank())) {
            throw ValidationFailedException.single("comment", "REQUIRED", "A comment is required for " + transitionCode);
        }
        RecordReference record = records.find(instance.recordId()).orElseThrow();
        Advance advance;
        try {
            advance = StateMachine.fire(spec, instance.currentState(), transitionCode, record.data());
        } catch (StateMachine.GuardRejectedException e) {
            auditDenied(actor.subject(), instance, task, transitionCode, "GUARD_REJECTED");
            throw e;
        }
        apply(instance, task, spec, advance, record, actor.subject(), transitionCode, comment);
    }

    /** Timeout của bước (ADR 017): hệ thống kích hoạt transition onTimeout, không cần assignment/quyền người dùng. */
    @Transactional
    public void fireTimeout(UUID taskId, Instant now) {
        TaskRow task = repository.task(taskId, true).orElse(null);
        if (task == null || !"OPEN".equals(task.status()) || task.dueAt() == null || task.dueAt().isAfter(now)) {
            return;
        }
        InstanceRow instance = repository.instance(task.instanceId(), true).orElseThrow();
        WorkflowSpec spec = specs.get(instance.versionId());
        StateDef state = spec.state(task.stateCode());
        if (!"RUNNING".equals(instance.status()) || state.onTimeout() == null) {
            repository.clearDue(taskId);
            return;
        }
        RecordReference record = records.find(instance.recordId()).orElseThrow();
        try {
            Advance advance = StateMachine.fire(spec, instance.currentState(), state.onTimeout(), record.data());
            apply(instance, task, spec, advance, record, "system:timeout", state.onTimeout(), "Timeout");
        } catch (StateMachine.GuardRejectedException e) {
            log.warn("Timeout transition {} rejected by guard for task {}", state.onTimeout(), taskId);
            repository.clearDue(taskId);
        }
    }

    /** Rút/hủy quy trình: chủ hồ sơ hoặc người có MANAGE trên wf:&lt;CODE&gt;. */
    @Transactional
    public void cancel(Actor actor, UUID instanceId, String reason) {
        InstanceRow instance = repository.instance(instanceId, true).orElseThrow(() -> new NotFoundException("Workflow instance", instanceId));
        boolean owner = instance.recordOwner().equals(actor.subject());
        if (!owner && !permissions.isAllowed(actor.subject(), resource(instance.definitionCode()), Action.MANAGE)) {
            throw new NotFoundException("Workflow instance", instanceId);
        }
        if (!"RUNNING".equals(instance.status())) {
            throw new InvalidStateException("Workflow is not running");
        }
        Instant now = Instant.now();
        repository.cancelOpenTasks(instanceId, actor.subject());
        repository.insertHistory(instanceId, null, instance.currentState(), instance.currentState(), "cancel", false,
                actor.subject(), reason);
        repository.updateInstance(instanceId, instance.version(), "CANCELLED", instance.currentState(), "CANCELLED", now);
        recordLifecycle.complete(instance.recordId(), RecordOutcome.CANCELLED, actor.subject());
        events.publishEvent(new WorkflowCompleted(TenantContext.require().value(), instanceId, instance.recordId(),
                instance.recordCode(), "CANCELLED", instance.recordOwner(), reason, now));
        outbox.append("workflow", instanceId.toString(), "WorkflowCompleted", 1,
                Map.of("instanceId", instanceId.toString(), "recordId", instance.recordId().toString(), "outcome", "CANCELLED"));
        audit(actor.subject(), "WORKFLOW_CANCELLED", "workflow", instanceId.toString(), BusinessAuditEvent.Outcome.SUCCESS, Map.of());
    }

    // ------------------------------------------------------------------ nội bộ

    boolean isAssignee(String subject, TaskRow task) {
        return switch (task.assigneeType()) {
            case "USER" -> task.assigneeValue().equals(subject);
            case "ROLE" -> roles.rolesOf(subject).contains(task.assigneeValue());
            default -> false;
        };
    }

    private void apply(InstanceRow instance, TaskRow task, WorkflowSpec spec, Advance advance, RecordReference record,
                       String actor, String transitionCode, String comment) {
        Instant now = Instant.now();
        repository.completeTask(task.id(), task.version(), "COMPLETED", actor, transitionCode, comment);
        boolean first = true;
        for (Step step : advance.steps()) {
            repository.insertHistory(instance.id(), first ? task.id() : null, step.from(), step.to(), step.transitionCode(),
                    step.automatic(), step.automatic() ? SYSTEM : actor, first ? comment : null);
            first = false;
        }
        if (advance.ended()) {
            repository.updateInstance(instance.id(), instance.version(), "COMPLETED", advance.currentState(),
                    advance.outcome().name(), now);
            finish(instance, advance, actor, comment, now);
        } else {
            repository.updateInstance(instance.id(), instance.version(), "RUNNING", advance.currentState(), null, null);
            createTask(instance, spec, advance.currentState(), record, now);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instanceId", instance.id().toString());
        payload.put("recordId", instance.recordId().toString());
        payload.put("from", task.stateCode());
        payload.put("to", advance.currentState());
        payload.put("transition", transitionCode);
        outbox.append("workflow", instance.id().toString(), "WorkflowTransitioned", 1, payload);
        audit(actor, "WORKFLOW_TRANSITION", "workflow", instance.id().toString(), BusinessAuditEvent.Outcome.SUCCESS,
                Map.of("task", task.id().toString(), "transition", transitionCode, "from", task.stateCode(),
                        "to", advance.currentState(), "record", instance.recordCode()));
    }

    private void finish(InstanceRow instance, Advance advance, String actor, String comment, Instant now) {
        recordLifecycle.complete(instance.recordId(), RecordOutcome.valueOf(advance.outcome().name()), actor);
        events.publishEvent(new WorkflowCompleted(TenantContext.require().value(), instance.id(), instance.recordId(),
                instance.recordCode(), advance.outcome().name(), instance.recordOwner(), comment, now));
        outbox.append("workflow", instance.id().toString(), "WorkflowCompleted", 1, Map.of(
                "instanceId", instance.id().toString(), "recordId", instance.recordId().toString(),
                "outcome", advance.outcome().name()));
    }

    private void createTask(InstanceRow instance, WorkflowSpec spec, String stateCode, RecordReference record, Instant now) {
        StateDef state = spec.state(stateCode);
        AssignmentRule.Assignee assignee = state.assignment().resolve(record.owner(), record.data());
        Instant due = state.timeout() == null ? null : now.plus(state.timeout());
        UUID taskId = Ids.newId();
        repository.insertTask(taskId, instance.id(), stateCode, state.name(), assignee.type(), assignee.value(), due);
        events.publishEvent(new TaskAssigned(TenantContext.require().value(), taskId, instance.id(), instance.recordId(),
                instance.recordCode(), state.name(), assignee.type(), assignee.value(), due));
    }

    private void deny(Actor actor, InstanceRow instance, TaskRow task, String transitionCode, String reason) {
        auditDenied(actor.subject(), instance, task, transitionCode, reason);
        throw new ForbiddenException("Transition " + transitionCode + " denied: " + reason);
    }

    private void auditDenied(String actor, InstanceRow instance, TaskRow task, String transitionCode, String reason) {
        auditTx.executeWithoutResult(s -> audit(actor, "WORKFLOW_TRANSITION", "workflow", instance.id().toString(),
                BusinessAuditEvent.Outcome.DENIED, Map.of("task", task.id().toString(), "transition", transitionCode,
                        "reason", reason)));
    }

    private void audit(String actor, String action, String resourceType, String resourceId, BusinessAuditEvent.Outcome outcome,
                       Map<String, String> detail) {
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor, action, resourceType, resourceId,
                outcome, detail, CorrelationId.current(), null));
    }
}
